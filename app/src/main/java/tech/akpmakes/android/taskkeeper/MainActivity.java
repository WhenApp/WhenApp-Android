package tech.akpmakes.android.taskkeeper;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Map;

import tech.akpmakes.android.taskkeeper.firebase.WhenAdapter;
import tech.akpmakes.android.taskkeeper.models.WhenEvent;
import tech.akpmakes.android.taskkeeper.BuildConfig;

public class MainActivity extends AppCompatActivity implements FirebaseAuth.AuthStateListener {
    public static final int WHEN_EVENT_REQUEST = 6900;
    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;
    private FirebaseRemoteConfig mRemoteConfig;
    private Query mDBQuery;
    private RecyclerView mRecyclerView;
    private FirebaseRecyclerAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private Map<String, WhenEvent> localCopyOfItems;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(this);

        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);

        mRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings remoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mRemoteConfig.setConfigSettings(remoteConfigSettings);
        mRemoteConfig.setDefaults(R.xml.remote_config_defaults);

        long cacheExpiration = 43200; // 12 hours, to match Firebase best practices.
        if(mRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mRemoteConfig.fetch(cacheExpiration)
            .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        mRemoteConfig.activateFetched();
                    }
                    applyRemoteConfig();
                }
            });

        setContentView(R.layout.activity_main);
        mRecyclerView = findViewById(R.id.events_list);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setHapticFeedbackEnabled(true);

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mRecyclerView.getContext(),
                mLayoutManager.getOrientation());
        mRecyclerView.addItemDecoration(dividerItemDecoration);

        ItemTouchHelper mIth = new ItemTouchHelper(new SwipeHandler(this));
        mIth.attachToRecyclerView(mRecyclerView);
    }

    private void applyRemoteConfig() {
        // NOOP for now.
    }

    public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
        updateUI(auth.getCurrentUser());

        boolean isSignedIn = (auth.getCurrentUser() != null && !auth.getCurrentUser().isAnonymous());

        if(mDBQuery != null && localCopyOfItems != null && isSignedIn) {
            for(Map.Entry<String, WhenEvent> entry : localCopyOfItems.entrySet()) {
                mDBQuery.getRef().child(entry.getKey()).setValue(entry.getValue());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
        updateAuth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mAdapter != null) {
            mAdapter.cleanup();
        }
    }

    private void updateAuth() {
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null) {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // Sign in success, update UI with the signed-in user's information
                                Log.d(TAG, "signInAnonymously:success");
                                FirebaseUser user = mAuth.getCurrentUser();
                                updateUI(user);
                            } else {
                                // If sign in fails, display a message to the user.
                                Log.w(TAG, "signInAnonymously:failure", task.getException());
                                Snackbar.make(findViewById(R.id.events_list), R.string.sign_in_failure,
                                        Snackbar.LENGTH_LONG).show();
                                updateUI(null);
                            }
                        }
                    });
        } else {
            updateUI(currentUser);
        }
    }

    private void updateUI(FirebaseUser user) {
        if(user == null) {
            return; // TODO: display an error
        }

        mDBQuery = FirebaseDatabase.getInstance().getReference("events/" + user.getUid()).orderByChild("when");
        mDBQuery.keepSynced(true);
        mAdapter = new WhenAdapter(this, mDBQuery);
        mRecyclerView.setAdapter(mAdapter);

        mDBQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<Map<String, WhenEvent>> t = new GenericTypeIndicator<Map<String, WhenEvent>>(){};
                localCopyOfItems = dataSnapshot.getValue(t);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return super.onOptionsItemSelected(item);
        } else if (itemId == R.id.add_item) {
            startActivityForResult(new Intent(this, TaskViewActivity.class), WHEN_EVENT_REQUEST);
            return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == WHEN_EVENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                WhenEvent evt = new WhenEvent(data);

                if(evt.getName().length() == 0) {
                    Snackbar.make(findViewById(R.id.events_list), R.string.snackbar_save_error_name,
                            Snackbar.LENGTH_LONG).show();
                    return;
                }

                if (mDBQuery != null) {
                    if(data.hasExtra("whenKey")) {
                        mDBQuery.getRef().child(data.getStringExtra("whenKey")).setValue(evt);
                    } else {
                        mDBQuery.getRef().push().setValue(evt);
                    }

                    Snackbar.make(findViewById(R.id.events_list), R.string.snackbar_save_success,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(findViewById(R.id.events_list), R.string.snackbar_save_error,
                            Snackbar.LENGTH_LONG).show();
                }
            }
        }
    }

    FirebaseRecyclerAdapter getAdapter() {
        return mAdapter;
    }
}
