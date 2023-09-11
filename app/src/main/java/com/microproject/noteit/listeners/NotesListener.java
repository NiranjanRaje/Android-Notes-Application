package com.microproject.noteit.listeners;

import android.view.View;

import com.microproject.noteit.entities.Note;

public interface NotesListener {
    void onNoteClicked(View view, Note note, int position);

    void onNoteLongClicked(View view, Note note, int position);
}
