const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write watchlist", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watchlist")
      .doc("cs2")
      .set({
        userId: ALICE_UID,
        gameTitle: "Counter-Strike 2",
        targetPriceEur: 12.0,
        preferredCurrency: "SEK",
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
});

test("Owner can create, list, update, and delete watchlist item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("watchlist")
    .doc("phantom-blade-zero");

  const ts = pastDate();
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      gameTitle: "Phantom Blade Zero",
      targetPriceEur: 39.99,
      preferredCurrency: "SEK",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").get()
  );

  await assertSucceeds(
    docRef.update({
      targetPriceEur: 34.99,
      preferredCurrency: "EUR",
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user access is denied", async () => {
  const ts = pastDate();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("watchlist")
    .doc("rust")
    .set({
      userId: ALICE_UID,
      gameTitle: "Rust",
      targetPriceEur: 18.0,
      preferredCurrency: "SEK",
      createdAt: ts,
      updatedAt: ts,
    });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("watchlist").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watchlist")
      .doc("rust")
      .delete()
  );
});

test("Shadow update with ghost field or invalid currency is rejected", async () => {
  const ts = pastDate();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watchlist")
      .doc("rust")
      .set({
        userId: ALICE_UID,
        gameTitle: "Rust",
        targetPriceEur: 18.0,
        preferredCurrency: "SEK",
        isAdmin: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watchlist")
      .doc("rust")
      .set({
        userId: ALICE_UID,
        gameTitle: "Rust",
        targetPriceEur: 18.0,
        preferredCurrency: "USD",
        createdAt: ts,
        updatedAt: ts,
      })
  );
});
