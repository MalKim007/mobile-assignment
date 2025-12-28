// Quick script to query Firestore predictions
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

// Initialize with application default credentials
initializeApp({
  projectId: 'mad-project-a859a'
});

const db = getFirestore();

async function queryPredictions() {
  try {
    const snapshot = await db.collection('predictions').limit(10).get();

    if (snapshot.empty) {
      console.log('No predictions found in Firestore.');
      return;
    }

    console.log(`Found ${snapshot.size} predictions:\n`);

    snapshot.forEach(doc => {
      const data = doc.data();
      console.log('---');
      console.log(`ID: ${data.dataId}`);
      console.log(`Name: ${data.name}`);
      console.log(`Expected: ${data.mappedAllergens || 'none'}`);
      console.log(`Predicted: ${data.predictedAllergens || 'none'}`);
      console.log(`Match: ${data.isMatch}`);
    });
  } catch (error) {
    console.error('Error querying Firestore:', error.message);
  }
}

queryPredictions();
