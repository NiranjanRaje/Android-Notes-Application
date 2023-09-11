package com.microproject.noteit;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.microproject.noteit.adapters.NotesAdapter;
import com.microproject.noteit.database.NotesDatabase;
import com.microproject.noteit.entities.Note;
import com.microproject.noteit.listeners.NotesListener;
import com.tapadoo.alerter.Alerter;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.shreyaspatil.MaterialDialog.MaterialDialog;
import hotchemi.android.rate.AppRate;
import maes.tech.intentanim.CustomIntent;

public class MainActivity extends AppCompatActivity implements NotesListener, NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private ConstraintLayout contentView;
    private ImageView imageEmpty;
    private ImageButton navigationMenu;
    private TextView textEmpty;
    private EditText inputSearch;
    private RecyclerView notesRecyclerView;
    private BottomAppBar bottomAppBar;
    private FloatingActionButton addNoteFloatingBtn;

    private List<Note> noteList;
    private NotesAdapter notesAdapter;

    public static final int REQUEST_CODE_ADD_NOTE = 1;
    public static final int REQUEST_CODE_UPDATE_NOTE = 2;
    public static final int REQUEST_CODE_SHOW_NOTES = 3;


    private int noteClickedPosition = -1;
    private ActionMode actionMode;


    private static final float END_SCALE = 0.8f;

    private AppUpdateManager mAppUpdateManager;
    private InstallStateUpdatedListener installStateUpdatedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setNavigationBarColor(ContextCompat.getColor(MainActivity.this, R.color.colorQuickActionsBackground));

        AppRate.with(MainActivity.this)
                .setInstallDays(1)
                .setLaunchTimes(3)
                .setRemindInterval(1)
                .setShowLaterButton(true)
                .setShowNeverButton(false)
                .monitor();

        AppRate.showRateDialogIfMeetsConditions(MainActivity.this);

        initViews();
        setNavigationMenu();
        setActionOnViews();

        getNotes(REQUEST_CODE_SHOW_NOTES, false);
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.main_drawer_layout);
        navigationView = findViewById(R.id.main_navigation_menu);
        contentView = findViewById(R.id.content_view);
        navigationMenu = findViewById(R.id.main_navigation);
        imageEmpty = findViewById(R.id.image_empty);
        textEmpty = findViewById(R.id.text_empty);
        inputSearch = findViewById(R.id.input_search);
        notesRecyclerView = findViewById(R.id.notes_recycler_view);
        bottomAppBar = findViewById(R.id.main_bottom_app_bar);
        addNoteFloatingBtn = findViewById(R.id.floating_action_add_notes_btn);
    }

    private void setActionOnViews() {
        KeyboardVisibilityEvent.setEventListener(MainActivity.this, isOpen -> {
            if (!isOpen) {
                inputSearch.clearFocus();
            }
        });

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                notesAdapter.cancelTimer();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (noteList.size() != 0) {
                    notesAdapter.searchNotes(s.toString());
                }
            }
        });

        notesRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        noteList = new ArrayList<>();
        notesAdapter = new NotesAdapter(noteList, this);
        notesRecyclerView.setAdapter(notesAdapter);

        bottomAppBar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.add:
                    UIUtil.hideKeyboard(MainActivity.this);
                    Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
                    CustomIntent.customType(MainActivity.this, "left-to-right");
                    inputSearch.setText(null);
                    break;

            }
            return false;
        });

        addNoteFloatingBtn.setOnClickListener(v -> {
            UIUtil.hideKeyboard(MainActivity.this);
            Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
            startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
            CustomIntent.customType(MainActivity.this, "left-to-right");
            inputSearch.setText(null);
        });
    }

    private void setNavigationMenu() {
        navigationView.bringToFront();
        navigationView.setNavigationItemSelectedListener(MainActivity.this);

        navigationMenu.setOnClickListener(v -> {
            UIUtil.hideKeyboard(MainActivity.this);
            if (drawerLayout.isDrawerVisible(GravityCompat.END))
                drawerLayout.closeDrawer(GravityCompat.END);
            else drawerLayout.openDrawer(GravityCompat.END);
        });

        animateNavigationDrawer();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_note:
                Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
                CustomIntent.customType(MainActivity.this, "left-to-right");
                inputSearch.setText(null);
                break;

            case R.id.menu_app_info:
                //redirect user to app Settings
                Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                appInfoIntent.addCategory(Intent.CATEGORY_DEFAULT);
                appInfoIntent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
                startActivity(appInfoIntent);
                inputSearch.setText(null);
                drawerLayout.closeDrawer(GravityCompat.END);
                break;

            case R.id.note_menu_share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NOTED - Don't Bother Remembering!");
                String app_url = "https://play.google.com/store/apps/details?id=" + MainActivity.this.getPackageName();
                shareIntent.putExtra(Intent.EXTRA_TEXT, app_url);
                startActivity(Intent.createChooser(shareIntent, "Share via"));
                inputSearch.setText(null);
                drawerLayout.closeDrawer(GravityCompat.END);
                break;

        }
        return false;
    }

    private void animateNavigationDrawer() {
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                final float diffScaledOffset = slideOffset * (1 - END_SCALE);
                final float offsetScale = 1 - diffScaledOffset;
                contentView.setScaleX(offsetScale);
                contentView.setScaleY(offsetScale);

                final float xOffset = drawerView.getWidth() * slideOffset;
                final float xOffsetDiff = contentView.getWidth() * diffScaledOffset / 2;
                final float xTranslation = xOffsetDiff - xOffset;
                contentView.setTranslationX(xTranslation);
            }
        });
    }













    private void getNotes(final int requestCode, final boolean isNoteDeleted) {

        @SuppressLint("StaticFieldLeak")
        class GetNotesTask extends AsyncTask<Void, Void, List<Note>> {

            @Override
            protected List<Note> doInBackground(Void... voids) {
                return NotesDatabase
                        .getDatabase(getApplicationContext())
                        .noteDao().getAllNotes();
            }

            @Override
            protected void onPostExecute(List<Note> notes) {
                super.onPostExecute(notes);
                if (requestCode == REQUEST_CODE_SHOW_NOTES) {
                    noteList.addAll(notes);
                    notesAdapter.notifyDataSetChanged();
                    if (drawerLayout.isDrawerVisible(GravityCompat.END))
                        drawerLayout.closeDrawer(GravityCompat.END);
                } else if (requestCode == REQUEST_CODE_ADD_NOTE) {
                    noteList.add(0, notes.get(0));
                    notesAdapter.notifyItemInserted(0);
                    notesRecyclerView.smoothScrollToPosition(0);
                    if (drawerLayout.isDrawerVisible(GravityCompat.END))
                        drawerLayout.closeDrawer(GravityCompat.END);
                } else if (requestCode == REQUEST_CODE_UPDATE_NOTE) {
                    noteList.remove(noteClickedPosition);
                    if (isNoteDeleted) {
                        notesAdapter.notifyItemRemoved(noteClickedPosition);
                    } else {
                        noteList.add(noteClickedPosition, notes.get(noteClickedPosition));
                        notesAdapter.notifyItemChanged(noteClickedPosition);
                    }
                    if (drawerLayout.isDrawerVisible(GravityCompat.END))
                        drawerLayout.closeDrawer(GravityCompat.END);
                }

                if (noteList.size() != 0) {
                    imageEmpty.setVisibility(View.GONE);
                    textEmpty.setVisibility(View.GONE);
                } else {
                    imageEmpty.setVisibility(View.VISIBLE);
                    textEmpty.setVisibility(View.VISIBLE);
                }
            }
        }

        new GetNotesTask().execute();
    }

    @Override
    public void onNoteClicked(View view, Note note, int position) {
        noteClickedPosition = position;
        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
        intent.putExtra("isViewOrUpdate", true);
        intent.putExtra("note", note);
        startActivityForResult(intent, REQUEST_CODE_UPDATE_NOTE);
        CustomIntent.customType(MainActivity.this, "left-to-right");
        inputSearch.setText(null);
    }

    @Override
    public void onNoteLongClicked(View view, Note note, int position) {
        noteClickedPosition = position;
        view.setForeground(getDrawable(R.drawable.foreground_selected_note));
        if (actionMode != null) {
            return;
        }

        actionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.note_menu, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.note_menu_edit:
                        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                        intent.putExtra("isViewOrUpdate", true);
                        intent.putExtra("note", note);
                        startActivityForResult(intent, REQUEST_CODE_UPDATE_NOTE);
                        CustomIntent.customType(MainActivity.this, "left-to-right");
                        inputSearch.setText(null);
                        mode.finish();
                        return true;
                    case R.id.note_menu_share:

                            String content = note.getTitle() + "\n\n" + note.getSubtitle() + "\n\n" + note.getNoteText();
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
                            shareIntent.putExtra(Intent.EXTRA_TEXT, content);
                            startActivity(Intent.createChooser(shareIntent, "Share via"));
                        mode.finish();
                        return true;
                    case R.id.note_menu_delete:
                        showDeleteNoteDialog(note);
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
                view.setForeground(null);
            }
        });
    }

    private void showDeleteNoteDialog(Note note) {
        MaterialDialog materialDialog = new MaterialDialog.Builder(MainActivity.this)
                .setTitle("Are you sure?")
                .setMessage("Are you sure you want to delete this note?")
                .setAnimation(R.raw.lottie_delete)
                .setCancelable(false)
                .setPositiveButton("Delete", R.drawable.ic_material_dialog_delete, (dialogInterface, which) -> {
                    @SuppressLint("StaticFieldLeak")
                    class DeleteNoteTask extends AsyncTask<Void, Void, Void> {

                        @Override
                        protected Void doInBackground(Void... voids) {
                            NotesDatabase.getDatabase(getApplicationContext()).noteDao()
                                    .deleteNote(note);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            super.onPostExecute(aVoid);
                            noteList.remove(noteClickedPosition);
                            notesAdapter.notifyItemRemoved(noteClickedPosition);

                            if (noteList.size() != 0) {
                                imageEmpty.setVisibility(View.GONE);
                                textEmpty.setVisibility(View.GONE);
                            } else {
                                imageEmpty.setVisibility(View.VISIBLE);
                                textEmpty.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    new DeleteNoteTask().execute();
                    dialogInterface.dismiss();
                })
                .setNegativeButton("Cancel", R.drawable.ic_material_dialog_cancel, (dialogInterface, which) -> dialogInterface.dismiss())
                .build();
        materialDialog.show();
    }





    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_NOTE && resultCode == RESULT_OK) {
            getNotes(REQUEST_CODE_ADD_NOTE, false);
        } else if (requestCode == REQUEST_CODE_UPDATE_NOTE && resultCode == RESULT_OK) {
            if (data != null) {
                getNotes(REQUEST_CODE_UPDATE_NOTE, data.getBooleanExtra("isNoteDeleted", false));
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAppUpdateManager != null) {
            mAppUpdateManager.unregisterListener(installStateUpdatedListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAppUpdateManager != null) {
            mAppUpdateManager.unregisterListener(installStateUpdatedListener);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerVisible(GravityCompat.END))
            drawerLayout.closeDrawer(GravityCompat.END);
        else finishAffinity();
    }
}