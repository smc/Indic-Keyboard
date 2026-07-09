/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard.clipboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.latin.R;

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryEntry;
import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ClipboardHistoryAdapter
        extends RecyclerView.Adapter<ClipboardHistoryAdapter.ViewHolder> {
    private static final int THUMBNAIL_TARGET_SIZE = 384;
    private static final long NO_REVEALED_ENTRY = -1;

    public interface OnClipboardEntryActionListener {
        void onClipboardEntryClicked(ClipboardHistoryEntry entry);
        void onClipboardEntryDeleteClicked(ClipboardHistoryEntry entry);
    }

    private final ClipboardHistoryManager mClipboardHistoryManager;
    private final OnClipboardEntryActionListener mListener;
    private List<ClipboardHistoryEntry> mEntries = new ArrayList<>();
    private long mRevealedDeleteTimestamp = NO_REVEALED_ENTRY;

    ClipboardHistoryAdapter(final ClipboardHistoryManager clipboardHistoryManager,
            final OnClipboardEntryActionListener listener) {
        mClipboardHistoryManager = clipboardHistoryManager;
        mListener = listener;
    }

    void setEntries(final List<ClipboardHistoryEntry> entries) {
        mEntries = entries;
        mRevealedDeleteTimestamp = NO_REVEALED_ENTRY;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.clipboard_history_item, parent, false /* attachToRoot */));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final ClipboardHistoryEntry entry = mEntries.get(position);
        if (entry.isImage()) {
            holder.mText.setVisibility(View.GONE);
            holder.mImage.setVisibility(View.VISIBLE);
            holder.mImage.setImageBitmap(
                    decodeThumbnail(mClipboardHistoryManager.imageFileFor(entry)));
        } else {
            holder.mImage.setVisibility(View.GONE);
            holder.mText.setVisibility(View.VISIBLE);
            holder.mText.setText(entry.getDisplayText());
        }
        holder.mDelete.setVisibility(
                entry.mTimestamp == mRevealedDeleteTimestamp ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (mRevealedDeleteTimestamp != NO_REVEALED_ENTRY) {
                setRevealedDelete(NO_REVEALED_ENTRY);
                return;
            }
            mListener.onClipboardEntryClicked(entry);
        });
        holder.itemView.setOnLongClickListener(v -> {
            setRevealedDelete(entry.mTimestamp);
            return true;
        });
        holder.mDelete.setOnClickListener(v -> mListener.onClipboardEntryDeleteClicked(entry));
    }

    private void setRevealedDelete(final long timestamp) {
        mRevealedDeleteTimestamp = timestamp;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    private static Bitmap decodeThumbnail(final File imageFile) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        int sampleSize = 1;
        while (options.outWidth / (sampleSize * 2) >= THUMBNAIL_TARGET_SIZE
                && options.outHeight / (sampleSize * 2) >= THUMBNAIL_TARGET_SIZE) {
            sampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView mText;
        final ImageView mImage;
        final ImageButton mDelete;

        ViewHolder(final View itemView) {
            super(itemView);
            itemView.setBackground(
                    ClipboardHistoryView.createRoundedBackground(itemView.getContext(), 12));
            itemView.setClipToOutline(true);
            mText = itemView.findViewById(R.id.clipboard_entry_text);
            mImage = itemView.findViewById(R.id.clipboard_entry_image);
            mDelete = itemView.findViewById(R.id.clipboard_entry_delete);
            mDelete.setBackground(
                    ClipboardHistoryView.createRoundedBackground(itemView.getContext(), 16));
        }
    }
}
