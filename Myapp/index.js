const firebase = require("firebase/app");
require("firebase/firestore");
require("firebase/messaging");

const config = {
  apiKey: "AIzaSyB7018lQkvnpq5waHFfnKKsI1yQH0jtqWk",
  authDomain: "dqra-eaadb.firebaseapp.com",
  databaseURL: "https://dqra-eaadb.firebaseio.com",
  projectId: "dqra-eaadb",
  storageBucket: "dqra-eaadb.firebasestorage.app",
  messagingSenderId: "1000422386055",
  appId: "1:1000422386055:android:a73c7272e6f34104e8cb22",
};

// Initialize Firebase
firebase.initializeApp(config);

// Test Firestore
const testCollection = firebase.firestore().collection("testCollection");

// Add a test document
testCollection.add({
  message: "Firebase is connected!",
  timestamp: firebase.firestore.FieldValue.serverTimestamp(),
})
    .then(() => {
      console.log("Test document added successfully!");
    })
    .catch((error) => {
      console.error("Error adding document: ", error);
    });
