import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, query, orderByChild, equalTo, onDisconnect, goOffline, serverTimestamp, startAt, endAt, limitToFirst } from 'firebase/database';
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

const trackingPath = 'families/family-a/trackingRequests/bob';
const trackingAckPath = 'families/family-a/trackingAcks/bob';
let trackingSerial = 0;
const trackingCommand = (mode, extra = {}) => ({
  id: String(++trackingSerial).padStart(36, '0'), requesterUid: 'alice', mode, requestedAt: serverTimestamp(), ...extra,
});
const trackingAck = request => ({ requestId: request.id, mode: request.mode, respondedAt: serverTimestamp() });

test('every member may select each tracking mode for themselves and another member', async () => {
  const db = database('alice');
  for (const mode of ['periodic', 'live', 'stopped']) {
    await assertSucceeds(set(ref(db, trackingPath), trackingCommand(mode)));
    await assertSucceeds(set(ref(db, 'families/family-a/trackingRequests/alice'), trackingCommand(mode)));
  }
});

test('tracking commands reject outsiders spoofed authors invalid targets and malformed payloads', async () => {
  await assertFails(set(ref(database(), trackingPath), trackingCommand('stopped')));
  await assertFails(set(ref(database('eve'), trackingPath), trackingCommand('stopped', { requesterUid: 'eve' })));
  const db = database('alice');
  for (const invalid of [trackingCommand('other'), trackingCommand('live', { requesterUid: 'bob' }),
    trackingCommand('live', { requestedAt: 1 }), trackingCommand('live', { extra: true }),
    trackingCommand('live', { id: 'short' }), { mode: 'stopped' }]) {
    await assertFails(set(ref(db, trackingPath), invalid));
  }
  await assertFails(set(ref(db, 'families/family-a/trackingRequests/eve'), trackingCommand('live')));
  await assertFails(set(ref(db, 'families/family-a/trackingRequests/missing'), trackingCommand('live')));
});

test('the newest command replaces earlier senders and only its target may confirm its exact mode', async () => {
  const first = trackingCommand('live');
  await assertSucceeds(set(ref(database('alice'), trackingPath), first));
  await assertSucceeds(set(ref(database('bob'), trackingAckPath), trackingAck(first)));
  const second = trackingCommand('stopped', { requesterUid: 'bob' });
  await assertSucceeds(set(ref(database('bob'), trackingPath), second));
  await assertFails(set(ref(database('bob'), trackingAckPath), trackingAck(first)));
  await assertFails(set(ref(database('alice'), trackingAckPath), trackingAck(second)));
  await assertFails(set(ref(database('bob'), trackingAckPath), { ...trackingAck(second), mode: 'live' }));
  await assertSucceeds(set(ref(database('bob'), trackingAckPath), trackingAck(second)));
  assert.equal((await get(ref(database('alice'), trackingPath))).val().id, second.id);
});

test('paused sharing keeps control presence and a complete disconnect disables both', async () => {
  const path = ref(database('bob'), 'families/family-a/devices/bob');
  await assertSucceeds(set(path, device({ sharing: false, mode: 'stopped', controlReady: true })));
  await assertSucceeds(onDisconnect(path).set(device({ sharing: false, mode: 'stopped', controlReady: false })));
  await assertFails(set(path, device({ controlReady: 'yes' })));
  await assertFails(set(ref(database('alice'), 'families/family-a/devices/bob'), device({ controlReady: true })));
});

test('revoked command authors cannot leave an executable confirmed command', async () => {
  const request = trackingCommand('periodic');
  await assertSucceeds(set(ref(database('alice'), trackingPath), request));
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), 'memberships/alice'), null));
  await assertFails(set(ref(database('bob'), trackingAckPath), trackingAck(request)));
  await assertFails(set(ref(database('alice'), trackingPath), trackingCommand('live')));
});

test('expired commands and forged confirmations are rejected', async () => {
  const request = trackingCommand('live', { requestedAt: Date.now() - 900001 });
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), trackingPath), request));
  await assertFails(set(ref(database('bob'), trackingAckPath), trackingAck(request)));
  const fresh = trackingCommand('periodic');
  await assertSucceeds(set(ref(database('alice'), trackingPath), fresh));
  await assertFails(set(ref(database('bob'), trackingAckPath), { ...trackingAck(fresh), respondedAt: 1 }));
  await assertFails(set(ref(database('bob'), trackingAckPath), { ...trackingAck(fresh), extra: true }));
  await assertFails(set(ref(database('alice'), trackingPath), null));
});

// History is a separate, indexed tree, never a child of the live family snapshot.
const historyPath = uid => `history/family-a/${uid}`;
const boundaryPath = uid => `families/family-a/historyDeletedThrough/${uid}`;
const historyPoint = (measuredAt = Date.now(), extra = {}) => ({ latitude: 52.23, longitude: 21.01,
  accuracyMeters: 20, batteryPercent: 75, measuredAt, receivedAt: serverTimestamp(), ...extra });
const historyQuery = (db, uid, first, last, limit = 10000) => query(ref(db, historyPath(uid)),
  orderByChild('measuredAt'), startAt(first - 0.5), endAt(last + 0.5), limitToFirst(limit));
const cleanupQuery = (db, uid, through) => query(ref(db, historyPath(uid)),
  orderByChild('measuredAt'), endAt(through + 0.5), limitToFirst(1000));

test('history upload is owner-only, idempotent and immutable, with no unrestricted family download', async () => {
  const now = Date.now();
  const db = database('bob');
  const path = ref(db, `${historyPath('bob')}/stable-id`);
  await assertSucceeds(set(path, historyPoint(now)));
  await assertSucceeds(set(path, historyPoint(now)));
  await assertFails(set(path, historyPoint(now, { latitude: 40 })));
  await assertFails(set(ref(database('alice'), `${historyPath('bob')}/spoofed`), historyPoint(now)));
  await assertFails(set(ref(database('eve'), `${historyPath('eve')}/foreign-family`), historyPoint(now)));
  await assertFails(set(ref(database(), `${historyPath('bob')}/anonymous`), historyPoint(now)));
  const result = await assertSucceeds(get(historyQuery(database('alice'), 'bob', now - 1000, now + 1000)));
  assert.equal(result.size, 1);
  await assertFails(get(ref(database('alice'), 'history')));
  await assertFails(get(ref(database('alice'), 'history/family-a')));
  await assertFails(get(ref(database('alice'), historyPath('bob'))));
  await assertFails(get(historyQuery(database('eve'), 'bob', now - 1000, now + 1000)));
});

test('history reads require a bounded single day and point limit', async () => {
  const now = Date.now(); const db = database('alice');
  await assertSucceeds(get(historyQuery(db, 'bob', now - 86400000, now, 10000)));
  await assertFails(get(historyQuery(db, 'bob', now - 2 * 86400000, now, 10000)));
  await assertFails(get(historyQuery(db, 'bob', now - 1000, now, 10001)));
  await assertFails(get(query(ref(db, historyPath('bob')), orderByChild('measuredAt'), startAt(now - 1000), endAt(now))));
  await assertFails(get(query(ref(db, historyPath('bob')), orderByChild('measuredAt'), startAt(now - 1000), limitToFirst(10000))));
});

test('history rejects expired future malformed and forged timestamps', async () => {
  const db = database('bob'); const now = Date.now();
  for (const invalid of [historyPoint(now - 30 * 86400000 - 10000), historyPoint(now + 120000),
    historyPoint(now, { latitude: 91 }), historyPoint(now, { longitude: -181 }), historyPoint(now, { accuracyMeters: 0 }),
    historyPoint(now, { batteryPercent: 101 }), historyPoint(now, { receivedAt: 1 }), historyPoint(now, { unexpected: 1 }),
    { latitude: 52 }, historyPoint(now, { measuredAt: 'today' }), historyPoint(now + 0.5)]) {
    await assertFails(set(ref(db, `${historyPath('bob')}/invalid`), invalid));
  }
});

test('deletion watermark is owner-only, monotonic and prevents resurrection even from old clients', async () => {
  const now = Date.now(); const db = database('bob');
  await assertSucceeds(set(ref(db, `${historyPath('bob')}/old`), historyPoint(now - 5000)));
  await assertSucceeds(set(ref(db, 'families/family-a/locations/bob'), point({ measuredAt: now - 5000 })));
  await assertFails(set(ref(database('alice'), boundaryPath('bob')), now));
  await assertFails(set(ref(database('eve'), boundaryPath('bob')), now));
  await assertFails(set(ref(db, `${historyPath('bob')}/old`), null));
  await assertSucceeds(set(ref(db, boundaryPath('bob')), now - 1000));
  await assertFails(set(ref(db, boundaryPath('bob')), now - 2000));
  await assertFails(set(ref(db, boundaryPath('bob')), now + 120000));
  await assertFails(set(ref(db, boundaryPath('bob')), now + 0.5));
  await assertFails(set(ref(db, boundaryPath('bob')), null));
  await assertSucceeds(set(ref(db, `${historyPath('bob')}/old`), null));
  await assertSucceeds(set(ref(db, 'families/family-a/locations/bob'), null));
  await assertFails(set(ref(db, `${historyPath('bob')}/old-retry`), historyPoint(now - 5000)));
  await assertFails(set(ref(db, 'families/family-a/locations/bob'), point({ measuredAt: now - 5000 })));
  await assertSucceeds(set(ref(db, `${historyPath('bob')}/new`), historyPoint(now)));
  await assertSucceeds(set(ref(db, 'families/family-a/locations/bob'), point({ measuredAt: now })));
  await assertFails(set(ref(db, 'families/family-a/locations/bob'), null));
  await assertFails(get(historyQuery(database('alice'), 'bob', now - 10000, now + 1000)));
  assert.equal((await assertSucceeds(get(historyQuery(database('alice'), 'bob', now, now + 1000)))).size, 1);
});

test('bounded cleanup removes expired history for inactive and departed members without deleting recent points', async () => {
  const now = Date.now(); const expired = now - 31 * 86400000;
  await env.withSecurityRulesDisabled(async context => {
    await set(ref(context.database(), `${historyPath('gone')}/expired`), historyPoint(expired));
    await set(ref(context.database(), `${historyPath('gone')}/recent`), historyPoint(now));
    await set(ref(context.database(), 'families/family-a/historyOwners/gone'), true);
  });
  const db = database('alice');
  const page = await assertSucceeds(get(cleanupQuery(db, 'gone', now - 30 * 86400000 - 2000)));
  assert.equal(page.size, 1);
  await assertSucceeds(set(ref(db, `${historyPath('gone')}/expired`), null));
  await assertFails(set(ref(db, `${historyPath('gone')}/recent`), null));
  await assertFails(get(historyQuery(db, 'gone', now - 1000, now + 1000)));
  await assertFails(set(ref(db, 'families/family-a/historyOwners/bob'), true));
  await assertSucceeds(set(ref(database('bob'), 'families/family-a/historyOwners/bob'), true));
});

test('revoking membership blocks history reads writes and deletion requests', async () => {
  const now = Date.now(); const db = database('bob');
  await assertSucceeds(set(ref(db, `${historyPath('bob')}/point`), historyPoint(now)));
  await env.withSecurityRulesDisabled(context => set(ref(context.database(), 'memberships/bob'), null));
  await assertFails(get(historyQuery(db, 'bob', now - 1000, now + 1000)));
  await assertFails(get(historyQuery(database('alice'), 'bob', now - 1000, now + 1000)));
  await assertFails(set(ref(db, `${historyPath('bob')}/another`), historyPoint(now)));
  await assertFails(set(ref(db, boundaryPath('bob')), now));
});
