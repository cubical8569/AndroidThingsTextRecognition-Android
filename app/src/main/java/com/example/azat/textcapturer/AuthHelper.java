package com.example.azat.textcapturer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthHelper {

    private FirebaseUser mUser;

    public AuthHelper() {
        mUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    public FirebaseUser getUser() {
        return mUser;
    }
}
