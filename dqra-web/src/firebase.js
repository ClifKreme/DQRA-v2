import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyCQiNG3yM7HaaWB2T7EstuvpzqfnUvn15I",
  authDomain: "dqra-c5683.firebaseapp.com",
  projectId: "dqra-c5683",
  storageBucket: "dqra-c5683.firebasestorage.app",
  messagingSenderId: "122820919238",
  appId: "1:122820919238:android:986dd5694372e05678fcc7"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export default app;
