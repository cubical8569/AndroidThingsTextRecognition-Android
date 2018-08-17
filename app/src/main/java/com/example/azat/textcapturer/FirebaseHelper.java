package com.example.azat.textcapturer;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class FirebaseHelper {
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mStorageRootRef;

    public FirebaseHelper() {
        mFirebaseStorage = FirebaseStorage.getInstance();
        mStorageRootRef = mFirebaseStorage.getReference();
    }

    public void upload(StorageReference fileRef, byte[] data, final Context context) {
        UploadTask uploadTask = fileRef.putBytes(data);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
                // ...
                Toast.makeText(context, "Success", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void uploadResult(final byte[] data, AuthHelper helper, final Context context) {
        StorageReference dirRef = mStorageRootRef
                .child(helper.getUser().getUid())
                .child(Build.MODEL)
                .child(RecordingHelper.generateFolderName());

        StorageReference fileRef = dirRef.child("result_text.txt");
        upload(fileRef, data, context);
    }

    public void uploadFrame(final byte data[], AuthHelper helper, final Context context) {
        StorageReference dirRef = mStorageRootRef
                .child(helper.getUser().getUid())
                .child(Build.MODEL);

        StorageReference fileRef = dirRef.child("current_frame.jpg");
        upload(fileRef, data, context);
    }
}
