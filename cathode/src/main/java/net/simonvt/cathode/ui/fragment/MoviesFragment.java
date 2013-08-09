package net.simonvt.cathode.ui.fragment;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.provider.CathodeContract;
import net.simonvt.cathode.remote.PriorityTraktTaskQueue;
import net.simonvt.cathode.remote.TraktTaskQueue;
import net.simonvt.cathode.remote.sync.SyncTask;
import net.simonvt.cathode.ui.MoviesNavigationListener;
import net.simonvt.cathode.ui.adapter.MoviesAdapter;

public abstract class MoviesFragment extends AbsAdapterFragment
    implements LoaderManager.LoaderCallbacks<Cursor> {

  private static final String TAG = "MoviesFragment";

  @Inject TraktTaskQueue queue;

  @Inject PriorityTraktTaskQueue priorityQueue;

  private MoviesNavigationListener navigationListener;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      navigationListener = (MoviesNavigationListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(
          activity.toString() + " must implement MoviesNavigationListener");
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    CathodeApp.inject(getActivity(), this);
    setHasOptionsMenu(true);

    getLoaderManager().initLoader(getLoaderId(), null, this);
  }

  @Override
  public void onDestroy() {
    if (getActivity().isFinishing() || isRemoving()) {
      getLoaderManager().destroyLoader(getLoaderId());
    }
    super.onDestroy();
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.fragment_movies, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_refresh:
        queue.add(new SyncTask());
        return true;

      case R.id.menu_search:
        navigationListener.onStartMovieSearch();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onItemClick(AdapterView l, View v, int position, long id) {
    Cursor c = (Cursor) getAdapter().getItem(position);
    navigationListener.onDisplayMovie(id,
        c.getString(c.getColumnIndex(CathodeContract.Movies.TITLE)));
  }

  void setCursor(Cursor cursor) {
    if (getAdapter() == null) {
      setAdapter(new MoviesAdapter(getActivity(), cursor));
    } else {
      ((MoviesAdapter) getAdapter()).changeCursor(cursor);
    }
  }

  protected abstract int getLoaderId();

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    setCursor(data);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    setAdapter(null);
  }
}