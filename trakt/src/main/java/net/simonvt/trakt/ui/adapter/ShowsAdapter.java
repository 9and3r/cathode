package net.simonvt.trakt.ui.adapter;

import butterknife.InjectView;
import butterknife.Views;

import net.simonvt.trakt.R;
import net.simonvt.trakt.TraktApp;
import net.simonvt.trakt.provider.TraktContract;
import net.simonvt.trakt.scheduler.EpisodeTaskScheduler;
import net.simonvt.trakt.scheduler.ShowTaskScheduler;
import net.simonvt.trakt.ui.LibraryType;
import net.simonvt.trakt.util.DateUtils;
import net.simonvt.trakt.util.LogWrapper;
import net.simonvt.trakt.widget.OverflowView;
import net.simonvt.trakt.widget.RemoteImageView;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import javax.inject.Inject;

public class ShowsAdapter extends CursorAdapter {

    private static final String TAG = "ShowsAdapter";

    @Inject EpisodeTaskScheduler mScheduler;

    @Inject ShowTaskScheduler mShowScheduler;

    private final LibraryType mLibraryType;

    public ShowsAdapter(Context context, LibraryType libraryType) {
        this(context, null, libraryType);
    }

    public ShowsAdapter(Context context, Cursor cursor, LibraryType libraryType) {
        super(context, cursor, 0);
        TraktApp.inject(context, this);
        mLibraryType = libraryType;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = LayoutInflater.from(context).inflate(R.layout.list_row_show, parent, false);

        ViewHolder vh = new ViewHolder(v);
        v.setTag(vh);

        return v;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final long id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));

        final String showPosterUrl = cursor.getString(cursor.getColumnIndex(TraktContract.Shows.POSTER));
        final String showTitle = cursor.getString(cursor.getColumnIndex(TraktContract.Shows.TITLE));
        final String showStatus = cursor.getString(cursor.getColumnIndex(TraktContract.Shows.STATUS));

        final int showAirdateCount = cursor.getInt(cursor.getColumnIndex(TraktContract.Shows.AIRDATE_COUNT));
        final int showUnairedCount = cursor.getInt(cursor.getColumnIndex(TraktContract.Shows.UNAIRED_COUNT));
        int showTypeCount = 0;
        switch (mLibraryType) {
            case WATCHED:
            case WATCHLIST:
                showTypeCount = cursor.getInt(cursor.getColumnIndex(TraktContract.Shows.WATCHED_COUNT));
                break;

            case COLLECTION:
                showTypeCount = cursor.getInt(cursor.getColumnIndex(TraktContract.Shows.IN_COLLECTION_COUNT));
                break;
        }

        final int showAiredCount = showAirdateCount - showUnairedCount;

        final String episodeTitle = cursor.getString(cursor.getColumnIndex(TraktContract.Episodes.TITLE));
        final long episodeFirstAired = cursor.getLong(cursor.getColumnIndex(TraktContract.Episodes.FIRST_AIRED));
        final int episodeSeasonNumber = cursor.getInt(cursor.getColumnIndex(TraktContract.Episodes.SEASON));
        final int episodeNumber = cursor.getInt(cursor.getColumnIndex(TraktContract.Episodes.EPISODE));

        ViewHolder vh = (ViewHolder) view.getTag();

        vh.mTitle.setText(showTitle);

        vh.mProgressBar.setMax(showAiredCount);
        vh.mProgressBar.setProgress(showTypeCount);

        vh.mWatched.setText(showTypeCount + "/" + showAiredCount);

        String episodeText;
        if (episodeTitle == null) {
            episodeText = showStatus;
            vh.mFirstAired.setVisibility(View.GONE);
        } else {
            episodeText = "Next: " + episodeSeasonNumber + "x" + episodeNumber + " " + episodeTitle;
            vh.mFirstAired.setVisibility(View.VISIBLE);
            vh.mFirstAired.setText(DateUtils.secondsToDate(mContext, episodeFirstAired));
        }
        vh.mNextEpisode.setText(episodeText);
        vh.mNextEpisode.setEnabled(episodeTitle != null);

        vh.mOverflow.setVisibility(showAiredCount > 0 ? View.VISIBLE : View.INVISIBLE);
        vh.mOverflow.setListener(new OverflowView.OverflowActionListener() {
            @Override
            public void onPopupShown() {
            }

            @Override
            public void onPopupDismissed() {
            }

            @Override
            public void onActionSelected(int action) {
                switch (action) {
                    case R.id.action_watchlist_remove:
                        mShowScheduler.setIsInWatchlist(id, false);
                        break;

                    case R.id.action_watched:
                        mShowScheduler.watchedNext(id);
                        LogWrapper.v(TAG, "Watched item: " + id);
                        break;

                    case R.id.action_watched_all:
                        mShowScheduler.setWatched(id, true);
                        break;

                    case R.id.action_unwatch_all:
                        mShowScheduler.setWatched(id, false);
                        break;

                    case R.id.action_collection_add:
                        mShowScheduler.collectedNext(id);
                        LogWrapper.v(TAG, "Watched item: " + id);
                        break;

                    case R.id.action_collection_add_all:
                        mShowScheduler.setIsInCollection(id, true);
                        break;

                    case R.id.action_collection_remove_all:
                        mShowScheduler.setIsInCollection(id, false);
                        break;
                }
            }
        });

        vh.mOverflow.removeItems();
        switch (mLibraryType) {
            case WATCHLIST:
                if (showAiredCount - showTypeCount > 0) {
                    vh.mOverflow.addItem(R.id.action_watchlist_remove, R.string.action_watchlist_remove);
                }

            case WATCHED:
                if (showAiredCount - showTypeCount > 0) {
                    if (episodeTitle != null) {
                        vh.mOverflow.addItem(R.id.action_watched, R.string.action_watched_next);
                    }
                    if (showTypeCount < showAiredCount) {
                        vh.mOverflow.addItem(R.id.action_watched_all, R.string.action_watched_all);
                    }
                }
                if (showTypeCount > 0) {
                    vh.mOverflow.addItem(R.id.action_unwatch_all, R.string.action_unwatch_all);
                }
                break;

            case COLLECTION:
                if (showAiredCount - showTypeCount > 0) {
                    vh.mOverflow.addItem(R.id.action_collection_add, R.string.action_collect_next);
                    if (showTypeCount < showAiredCount) {
                        vh.mOverflow.addItem(R.id.action_collection_add_all, R.string.action_collection_add_all);
                    }
                }
                if (showTypeCount > 0) {
                    vh.mOverflow.addItem(R.id.action_collection_remove_all, R.string.action_collection_remove_all);
                }
                break;
        }

        vh.mPoster.setImage(showPosterUrl);
    }

    public static class ViewHolder {

        @InjectView(R.id.infoParent) View mInfoParent;
        @InjectView(R.id.title) TextView mTitle;
        @InjectView(R.id.watched) TextView mWatched;
        @InjectView(R.id.progress) ProgressBar mProgressBar;
        @InjectView(R.id.nextEpisode) TextView mNextEpisode;
        @InjectView(R.id.firstAired) TextView mFirstAired;
        @InjectView(R.id.overflow) OverflowView mOverflow;
        @InjectView(R.id.poster) RemoteImageView mPoster;

        ViewHolder(View v) {
            Views.inject(this, v);
        }
    }
}
