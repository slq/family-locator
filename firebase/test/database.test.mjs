import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, serverTimestamp } from 'firebase/database';

// The Node database SDK forwards WebSockets through HTTP_PROXY even for loopback.
// These tests connect only to the local emulator; never proxy their mock auth tokens.
for (const name of ['HTTP_PROXY', 'HTTPS_PROXY', 'http_proxy', 'https_proxy']) delete process.env[name];

let env;
const database = uid => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).database();
const point = (extra = {}) => ({
  label: 'Telefon testowy', latitude: 52.23, longitude: 21.01,
  accuracyMeters: 20, measuredAt: Date.now(), receivedAt: serverTimestamp(), batteryPercent: 75,
  ...extra,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-family-locator',
    database: { host: '127.0.0.1', port: 9000, rules: await readFile('firebase/database.rules.json', 'utf8') },
  });
}, { timeout: 20000 });
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async context => {
    await set(ref(context.database(), 'memberships'), {
      alice: { familyId: 'family-a' }, bob: { familyId: 'family-a' }, eve: { familyId: 'family-b' },
    });
  });
});

test('unauthenticated clients cannot read or upload coordinates', async () => {
  await assertFails(get(ref(database(), 'families/family-a/locations')));
  await assertFails(set(ref(database(), 'families/family-a/locations/alice'), point()));
});
test('members can write their own point and read their family', async () => {
  await assertSucceeds(set(ref(database('alice'), 'families/family-a/locations/alice'), point()));
  await assertSucceeds(get(ref(database('bob'), 'families/family-a/locations/alice')));
});
test('another family cannot read or overwrite a position', async () => {
  await assertFails(get(ref(database('eve'), 'families/family-a/locations')));
  await assertFails(set(ref(database('eve'), 'families/family-a/locations/eve'), point()));
});
test('family members cannot impersonate each other', async () => {
  await assertFails(set(ref(database('bob'), 'families/family-a/locations/alice'), point()));
});
test('clients cannot assign themselves to a family or enumerate memberships', async () => {
  await assertFails(set(ref(database('eve'), 'memberships/eve/familyId'), 'family-a'));
  await assertFails(get(ref(database('alice'), 'memberships')));
  await assertFails(get(ref(database('alice'), 'memberships/bob')));
  await assertSucceeds(get(ref(database('alice'), 'memberships/alice')));
});
test('an authenticated account without membership cannot read or write', async () => {
  await assertFails(get(ref(database('unknown'), 'families/family-a/locations')));
  await assertFails(set(ref(database('unknown'), 'families/family-a/locations/unknown'), point()));
});
test('old offline points cannot overwrite a newer location', async () => {
  const path = ref(database('alice'), 'families/family-a/locations/alice');
  const now = Date.now();
  await assertSucceeds(set(path, point({ measuredAt: now })));
  await assertFails(set(path, point({ measuredAt: now - 1000 })));
});
test('invalid coordinates, missing fields and unexpected fields are rejected', async () => {
  const path = ref(database('alice'), 'families/family-a/locations/alice');
  for (const invalid of [point({ latitude: 100 }), point({ longitude: -181 }), point({ accuracyMeters: 0 }),
    point({ batteryPercent: 101 }), point({ label: '' }), point({ label: 'x'.repeat(41) }),
    point({ latitude: '52' }), point({ admin: true }), { latitude: 52 }]) {
    await assertFails(set(path, invalid));
  }
});
test('future or expired timestamps and forged receipt time are rejected', async () => {
  const path = ref(database('alice'), 'families/family-a/locations/alice');
  await assertFails(set(path, point({ measuredAt: Date.now() + 120000 })));
  await assertFails(set(path, point({ measuredAt: Date.now() - 86401000 })));
  await assertFails(set(path, point({ receivedAt: 1 })));
});
test('revoking membership prevents further reads and writes', async () => {
  const db = database('alice');
  await assertSucceeds(set(ref(db, 'families/family-a/locations/alice'), point()));
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), 'memberships/alice'), null));
  await assertFails(set(ref(db, 'families/family-a/locations/alice'), point()));
  await assertFails(get(ref(database('alice'), 'families/family-a/locations')));
});
