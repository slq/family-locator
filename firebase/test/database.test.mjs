import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, query, orderByChild, equalTo, onDisconnect, goOffline, serverTimestamp } from 'firebase/database';
import assert from 'node:assert/strict';

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

const command = (extra = {}) => ({ id: '11111111-1111-4111-8111-111111111111', requestedAt: serverTimestamp(), ...extra });
const device = (extra = {}) => ({ sharing: true, mode: 'periodic', liveUntil: 0, updatedAt: serverTimestamp(), ...extra });
const commandPath = 'families/family-a/liveRequests/bob/alice';
const ackPath = 'families/family-a/liveAcks/bob/alice';
async function acknowledgment(extra = {}) {
  const request = (await get(ref(database('bob'), commandPath))).val();
  return { requestId: request.id, state: 'active', respondedAt: serverTimestamp(), expiresAt: request.requestedAt + 900000, ...extra };
}

test('a constrained roster query includes members without positions and hides other families', async () => {
  const result = await assertSucceeds(get(query(ref(database('alice'), 'memberships'), orderByChild('familyId'), equalTo('family-a'))));
  assert.deepEqual(Object.keys(result.val()).sort(), ['alice', 'bob']);
  await assertFails(get(query(ref(database('alice'), 'memberships'), orderByChild('familyId'), equalTo('family-b'))));
  await assertFails(get(query(ref(database('unknown'), 'memberships'), orderByChild('familyId'), equalTo('family-a'))));
  await assertFails(get(query(ref(database(), 'memberships'), orderByChild('familyId'), equalTo('family-a'))));
  await assertSucceeds(get(ref(database('alice'), 'families/family-a')));
  await assertFails(get(ref(database('eve'), 'families/family-a')));
});

test('every family member can name another current member without overwriting the name through GPS uploads', async () => {
  const namePath = 'families/family-a/memberNames/bob';
  await assertSucceeds(set(ref(database('alice'), namePath), 'Tata'));
  await assertSucceeds(set(ref(database('bob'), 'families/family-a/memberNames/alice'), 'Mama'));
  await assertSucceeds(set(ref(database('bob'), 'families/family-a/locations/bob'), point({ label: 'Telefon' })));
  assert.equal((await get(ref(database('bob'), namePath))).val(), 'Tata');
  for (const name of ['', '   ', 'x'.repeat(41), 123, { label: 'x' }, null]) {
    await assertFails(set(ref(database('alice'), namePath), name));
  }
  await assertFails(set(ref(database('eve'), namePath), 'Inna rodzina'));
  await assertFails(set(ref(database('alice'), 'families/family-a/memberNames/eve'), 'Obca osoba'));
});

test('every member may request fifteen minutes only in their own requester slot', async () => {
  await assertSucceeds(set(ref(database('alice'), commandPath), command()));
  await assertSucceeds(set(ref(database('bob'), 'families/family-a/liveRequests/alice/bob'), command()));
  await assertFails(set(ref(database('bob'), commandPath), command({ id: '22222222-2222-4222-8222-222222222222' })));
  await assertFails(set(ref(database('eve'), 'families/family-a/liveRequests/bob/eve'), command()));
  await assertFails(set(ref(database('alice'), 'families/family-a/liveRequests/eve/alice'), command()));
  for (const invalid of [command({ requestedAt: 1 }), command({ expiresAt: Date.now() + 3600000 }), command({ id: '' }), null]) {
    await assertFails(set(ref(database('alice'), commandPath), invalid));
  }
  // Reusing the same ID cannot extend the original request.
  await assertFails(set(ref(database('alice'), commandPath), command()));
});

test('only the target confirms the current request with its fixed expiry', async () => {
  await assertSucceeds(set(ref(database('alice'), commandPath), command()));
  const ack = await acknowledgment();
  await assertSucceeds(set(ref(database('bob'), ackPath), ack));
  await assertFails(set(ref(database('alice'), ackPath), ack));
  for (const extra of [{ requestId: 'wrong' }, { expiresAt: ack.expiresAt + 1 }, { state: 'unknown' }, { respondedAt: 1 }, { admin: true }]) {
    await assertFails(set(ref(database('bob'), ackPath), await acknowledgment(extra)));
  }
  await assertSucceeds(set(ref(database('bob'), ackPath), await acknowledgment({ state: 'stopped' })));
  await assertSucceeds(set(ref(database('alice'), commandPath), command({ id: '22222222-2222-4222-8222-222222222222' })));
  await assertFails(set(ref(database('bob'), ackPath), ack));
});

test('device heartbeats are owner-only and cannot fake an unlimited live session', async () => {
  const path = 'families/family-a/devices/bob';
  await assertSucceeds(set(ref(database('bob'), path), device()));
  await assertSucceeds(set(ref(database('bob'), path), device({ mode: 'live', liveUntil: Date.now() + 900000 })));
  await assertFails(set(ref(database('alice'), path), device()));
  for (const extra of [{ mode: 'live', liveUntil: Date.now() + 3600000 }, { mode: 'live' }, { sharing: false }, { updatedAt: 1 }, { admin: true }]) {
    await assertFails(set(ref(database('bob'), path), device(extra)));
  }
  await assertSucceeds(set(ref(database('bob'), path), device({ sharing: false, mode: 'stopped' })));
});

test('revoked members cannot rename, send requests, or receive family control data', async () => {
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), 'memberships/alice'), null));
  await assertFails(set(ref(database('alice'), 'families/family-a/memberNames/bob'), 'Tata'));
  await assertFails(set(ref(database('alice'), commandPath), command()));
  await assertFails(get(ref(database('alice'), 'families/family-a')));
  await assertFails(get(query(ref(database('alice'), 'memberships'), orderByChild('familyId'), equalTo('family-a'))));
  await assertFails(set(ref(database('bob'), 'families/family-a/liveRequests/alice/bob'), command()));
});

test('a disconnect marks the device stopped with a server heartbeat', async () => {
  const receiver = database('bob');
  const path = 'families/family-a/devices/bob';
  await assertSucceeds(onDisconnect(ref(receiver, path)).set(device({ sharing: false, mode: 'stopped' })));
  await assertSucceeds(set(ref(receiver, path), device({ mode: 'live', liveUntil: Date.now() + 900000 })));
  goOffline(receiver);
  const viewer = database('alice');
  let value;
  for (let attempt = 0; attempt < 30; attempt++) {
    value = (await get(ref(viewer, path))).val();
    if (!value.sharing) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(value.sharing, false);
  assert.equal(value.mode, 'stopped');
  assert.equal(value.liveUntil, 0);
  assert.ok(Math.abs(value.updatedAt - Date.now()) < 5000);
});

test('members choose their own icon and their family can read it including an automatic reset', async () => {
  const path = 'families/family-a/memberIcons/alice';
  for (const icon of ['cat', 'dog', 'fox', 'bear', 'panda', 'rabbit', 'koala', 'tiger', 'lion', 'owl', 'frog', 'penguin']) {
    await assertSucceeds(set(ref(database('alice'), path), icon));
    assert.equal((await assertSucceeds(get(ref(database('bob'), path)))).val(), icon);
  }
  await assertSucceeds(set(ref(database('alice'), path), null));
  assert.equal((await get(ref(database('bob'), path))).exists(), false);
});

test('icons cannot be changed by another member another family or an unauthenticated client', async () => {
  const path = 'families/family-a/memberIcons/alice';
  await assertSucceeds(set(ref(database('alice'), path), 'cat'));
  for (const uid of ['bob', 'eve', 'unknown', null]) {
    await assertFails(set(ref(database(uid), path), 'dog'));
    await assertFails(set(ref(database(uid), path), null));
  }
  await assertFails(get(ref(database('eve'), path)));
  await assertFails(get(ref(database(), path)));
  await assertFails(set(ref(database('alice'), 'families/family-b/memberIcons/alice'), 'fox'));
});

test('icon selection rejects malformed values and revoked owners', async () => {
  const path = 'families/family-a/memberIcons/alice';
  for (const invalid of ['', 'other', 'CAT', '🐱', 'https://example.com/avatar.png', 1, { icon: 'cat' }]) {
    await assertFails(set(ref(database('alice'), path), invalid));
  }
  await assertSucceeds(set(ref(database('alice'), path), 'cat'));
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), 'memberships/alice'), null));
  await assertFails(set(ref(database('alice'), path), 'dog'));
  await assertFails(set(ref(database('alice'), path), null));
});
