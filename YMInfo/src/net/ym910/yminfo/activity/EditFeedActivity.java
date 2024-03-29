/**
 * YMInfo
 *
 * Copyright (c) 2012-2013 WeiQiang ym910.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 * 
 *
 *     Permission is hereby granted, free of charge, to any person obtaining a copy
 *     of this software and associated documentation files (the "Software"), to deal
 *     in the Software without restriction, including without limitation the rights
 *     to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *     copies of the Software, and to permit persons to whom the Software is
 *     furnished to do so, subject to the following conditions:
 *
 *     The above copyright notice and this permission notice shall be included in
 *     all copies or substantial portions of the Software.
 *
 *     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *     AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *     LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *     THE SOFTWARE.
 */

package net.ym910.yminfo.activity;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import net.ym910.yminfo.R;
import net.ym910.yminfo.Constants;
import net.ym910.yminfo.adapter.EntriesCursorAdapter;
import net.ym910.yminfo.adapter.FiltersCursorAdapter;
import net.ym910.yminfo.provider.FeedData.FeedColumns;
import net.ym910.yminfo.provider.FeedData.FilterColumns;
import net.ym910.yminfo.utils.GeneralUtil;
import net.ym910.yminfo.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import com.androidquery.AQuery;
import com.androidquery.callback.ImageOptions;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Html;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.Toast;

public class EditFeedActivity extends ListActivity implements
		LoaderManager.LoaderCallbacks<Cursor> {

	static final String FEED_SEARCH_TITLE = "title";
	static final String FEED_SEARCH_URL = "rssUrl";
	static final String FEED_SEARCH_DESC = "desc";
	static final String FEED_SEARCH_HOME = "homePage";

	private static final String[] FEED_PROJECTION = new String[] {
			FeedColumns.NAME, FeedColumns.URL, FeedColumns.RETRIEVE_FULLTEXT };

	private EditText mNameEditText, mUrlEditText;
	private CheckBox mRetrieveFulltextCb;
	private ListView mFiltersListView;
	private String mPreviousName;

	private FiltersCursorAdapter mFiltersCursorAdapter;
	private ImageOptions options;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		setContentView(R.layout.feed_edit);
		setResult(RESULT_CANCELED);

		Intent intent = getIntent();

		mNameEditText = (EditText) findViewById(R.id.feed_title);
		mUrlEditText = (EditText) findViewById(R.id.feed_url);
		mRetrieveFulltextCb = (CheckBox) findViewById(R.id.retrieve_fulltext);
		mFiltersListView = (ListView) findViewById(android.R.id.list);
		View filtersLayout = findViewById(R.id.filters_layout);
		View buttonLayout = findViewById(R.id.button_layout);

		if (intent.getAction().equals(Intent.ACTION_INSERT)
				|| intent.getAction().equals(Intent.ACTION_SEND)) {
			setTitle(R.string.new_feed_title);

			filtersLayout.setVisibility(View.GONE);

			if (intent.hasExtra(Intent.EXTRA_TEXT)) {
				mUrlEditText.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
			}

			restoreInstanceState(savedInstanceState);
		} else if (intent.getAction().equals(Intent.ACTION_EDIT)) {
			setTitle(R.string.edit_feed_title);

			buttonLayout.setVisibility(View.GONE);

			mFiltersCursorAdapter = new FiltersCursorAdapter(this, null);
			mFiltersListView.setAdapter(mFiltersCursorAdapter);
			mFiltersListView
					.setOnItemLongClickListener(new OnItemLongClickListener() {
						@Override
						public boolean onItemLongClick(AdapterView<?> parent,
								View view, int position, long id) {
							startActionMode(mFilterActionModeCallback);
							mFiltersCursorAdapter.setSelectedFilter(position);
							mFiltersListView.invalidateViews();
							return true;
						}
					});

			getLoaderManager().initLoader(0, null, this);

			if (!restoreInstanceState(savedInstanceState)) {
				Cursor cursor = getContentResolver().query(intent.getData(),
						FEED_PROJECTION, null, null, null);

				if (cursor.moveToNext()) {
					mPreviousName = cursor.getString(0);
					mNameEditText.setText(mPreviousName);
					mUrlEditText.setText(cursor.getString(1));
					mRetrieveFulltextCb.setChecked(cursor.getInt(2) == 1);
					cursor.close();
				} else {
					cursor.close();
					Toast.makeText(EditFeedActivity.this, R.string.error,
							Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		}

		options = new ImageOptions();
		options.round = 15;
		options.fileCache = true;
		options.memCache = false;
		options.targetWidth = 50;
		options.fallback = AQuery.GONE;
	}

	@Override
	protected void onDestroy() {
		if (getIntent().getAction().equals(Intent.ACTION_EDIT)) {
			String url = mUrlEditText.getText().toString();
			ContentResolver cr = getContentResolver();

			Cursor cursor = getContentResolver().query(FeedColumns.CONTENT_URI,
					FeedColumns.PROJECTION_ID,
					FeedColumns.URL + Constants.DB_ARG, new String[] { url },
					null);

			if (cursor.moveToFirst()
					&& !getIntent().getData().getLastPathSegment()
							.equals(cursor.getString(0))) {
				cursor.close();
				Toast.makeText(EditFeedActivity.this,
						R.string.error_feed_url_exists, Toast.LENGTH_LONG)
						.show();
			} else {
				cursor.close();
				ContentValues values = new ContentValues();

				if (!url.startsWith(Constants.HTTP)
						&& !url.startsWith(Constants.HTTPS)) {
					url = Constants.HTTP + url;
				}
				values.put(FeedColumns.URL, url);

				String name = mNameEditText.getText().toString();

				values.put(FeedColumns.NAME, name.trim().length() > 0 ? name
						: null);
				values.put(FeedColumns.RETRIEVE_FULLTEXT,
						mRetrieveFulltextCb.isChecked() ? 1 : null);
				values.put(FeedColumns.FETCH_MODE, 0);
				values.putNull(FeedColumns.ERROR);

				cr.update(getIntent().getData(), values, null, null);
				if (!name.equals(mPreviousName)) {
					cr.notifyChange(FeedColumns.GROUPS_CONTENT_URI, null);
					cr.notifyChange(FeedColumns.GROUPED_FEEDS_CONTENT_URI, null);
				}
			}
		}

		super.onDestroy();
	}

	private boolean restoreInstanceState(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			mNameEditText.setText(savedInstanceState
					.getCharSequence(FeedColumns.NAME));
			mUrlEditText.setText(savedInstanceState
					.getCharSequence(FeedColumns.URL));
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putCharSequence(FeedColumns.NAME, mNameEditText.getText());
		outState.putCharSequence(FeedColumns.URL, mUrlEditText.getText());
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		}

		return true;
	}

	public void onClickAddFilter(View view) {
		final View dialogView = getLayoutInflater().inflate(
				R.layout.filter_edit, null);

		new AlertDialog.Builder(this)
				//
				.setTitle(R.string.filter_add_title)
				//
				.setView(dialogView)
				//
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								String filterText = ((EditText) dialogView
										.findViewById(R.id.filterText))
										.getText().toString();
								if (!GeneralUtil.isEmpty(filterText)) {
									String feedId = getIntent().getData()
											.getLastPathSegment();

									ContentValues values = new ContentValues();
									values.put(FilterColumns.FILTER_TEXT,
											filterText);
									values.put(
											FilterColumns.IS_REGEX,
											((CheckBox) dialogView
													.findViewById(R.id.regexCheckBox))
													.isChecked());
									values.put(
											FilterColumns.IS_APPLIED_TO_TITLE,
											((RadioButton) dialogView
													.findViewById(R.id.applyTitleRadio))
													.isChecked());

									ContentResolver cr = getContentResolver();
									cr.insert(
											FilterColumns
													.FILTERS_FOR_FEED_CONTENT_URI(feedId),
											values);
								}
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
							}
						}).show();
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		CursorLoader cursorLoader = new CursorLoader(this,
				FilterColumns.FILTERS_FOR_FEED_CONTENT_URI(getIntent()
						.getData().getLastPathSegment()), null, null, null,
				null);
		cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
		return cursorLoader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mFiltersCursorAdapter.changeCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mFiltersCursorAdapter.changeCursor(null);
	}
	
	public void onClickOk(View view) {
		// only in insert mode

		String url = mUrlEditText.getText().toString();
		if (GeneralUtil.isEmpty(url))
		{
			Toast.makeText(EditFeedActivity.this,
					R.string.url_empty, Toast.LENGTH_SHORT).show();
			return;
		}
		
		final ContentResolver cr = getContentResolver();

		if (!url.toLowerCase().startsWith(Constants.HTTP) && !url.toLowerCase().startsWith(Constants.HTTPS)) {
			url = Constants.HTTP + url;
		}
		
		try {
			new URL(url);
		} catch (MalformedURLException e) {
			Toast.makeText(EditFeedActivity.this,
					R.string.url_not_valid, Toast.LENGTH_SHORT).show();
			return;
		}
		ableToAdd(url, new NewURLRunnable() {
			public void run(String newurl) {
				Cursor cursor = cr.query(FeedColumns.CONTENT_URI, null, FeedColumns.URL
						+ Constants.DB_ARG, new String[] { newurl }, null);

				if (cursor.moveToFirst()) {
					cursor.close();
					Toast.makeText(EditFeedActivity.this,
							R.string.error_feed_url_exists, Toast.LENGTH_LONG).show();
				} else {
					cursor.close();
					ContentValues values = new ContentValues();

					values.put(FeedColumns.URL, newurl);
					values.putNull(FeedColumns.ERROR);

					String name = mNameEditText.getText().toString();

					if (name.trim().length() > 0) {
						values.put(FeedColumns.NAME, name);
					}
					values.put(FeedColumns.RETRIEVE_FULLTEXT, mRetrieveFulltextCb.isChecked() ? 1 : null);
					cr.insert(FeedColumns.CONTENT_URI, values);
					cr.notifyChange(FeedColumns.GROUPS_CONTENT_URI, null);
					cr.notifyChange(FeedColumns.GROUPED_FEEDS_CONTENT_URI, null);
				}

				setResult(RESULT_OK);
				finish();
			}
		});
	}
	
	private interface NewURLRunnable 
	{
		public void run(String newurl);
	}
	
	protected void ableToAdd(final String oldUrl, final NewURLRunnable runInUI)
	{
		
		final ProgressDialog pd = new ProgressDialog(
				EditFeedActivity.this);
		pd.setMessage(getString(R.string.checking));
		pd.setCancelable(true);
		pd.setIndeterminate(true);
		pd.show();

		getLoaderManager()
				.restartLoader(
						1,
						null,
						new LoaderCallbacks<String>() {

							@Override
							public Loader<String> onCreateLoader(
									int id, Bundle args) {
								return new AsyncTaskLoader<String>(EditFeedActivity.this) {
									public String loadInBackground() {
										return GeneralUtil.checkRSSAddress(oldUrl);
									}
								};
							}

							@Override
							public void onLoadFinished(
									Loader<String> loader,
									final String data) {
								pd.cancel();
								runInUI.run(data);
							}

							@Override
							public void onLoaderReset(
									Loader<String> arg0) {

							}
						}).forceLoad();
		
	}

	public void onClickCancel(View view) {
		finish();
	}

	private final ActionMode.Callback mFilterActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.edit_context_menu, menu);
			return true;
		}

		// Called each time the action mode is shown. Always called after
		// onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

			switch (item.getItemId()) {
			case R.id.menu_edit:
				Cursor c = mFiltersCursorAdapter.getCursor();
				if (c.moveToPosition(mFiltersCursorAdapter.getSelectedFilter())) {
					final View dialogView = getLayoutInflater().inflate(
							R.layout.filter_edit, null);
					final EditText filterText = (EditText) dialogView
							.findViewById(R.id.filterText);
					final CheckBox regexCheckBox = (CheckBox) dialogView
							.findViewById(R.id.regexCheckBox);
					final RadioButton applyTitleRadio = (RadioButton) dialogView
							.findViewById(R.id.applyTitleRadio);
					final RadioButton applyContentRadio = (RadioButton) dialogView
							.findViewById(R.id.applyContentRadio);

					filterText.setText(c.getString(c
							.getColumnIndex(FilterColumns.FILTER_TEXT)));
					regexCheckBox.setChecked(c.getInt(c
							.getColumnIndex(FilterColumns.IS_REGEX)) == 1);
					if (c.getInt(c
							.getColumnIndex(FilterColumns.IS_APPLIED_TO_TITLE)) == 1) {
						applyTitleRadio.setChecked(true);
					} else {
						applyContentRadio.setChecked(true);
					}

					final long filterId = mFiltersCursorAdapter
							.getItemId(mFiltersCursorAdapter
									.getSelectedFilter());
					new AlertDialog.Builder(EditFeedActivity.this)
							//
							.setTitle(R.string.filter_edit_title)
							//
							.setView(dialogView)
							//
							.setPositiveButton(android.R.string.ok,
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(
												DialogInterface dialog,
												int which) {
											EntriesCursorAdapter.newCachedThreadPool
													.submit(new Runnable() {
														public void run() {
															String filter = filterText
																	.getText()
																	.toString();
															if (!GeneralUtil
																	.isEmpty(filter)) {
																ContentResolver cr = getContentResolver();
																ContentValues values = new ContentValues();
																values.put(
																		FilterColumns.FILTER_TEXT,
																		filter);
																values.put(
																		FilterColumns.IS_REGEX,
																		regexCheckBox
																				.isChecked());
																values.put(
																		FilterColumns.IS_APPLIED_TO_TITLE,
																		applyTitleRadio
																				.isChecked());
																if (cr.update(
																		FilterColumns.CONTENT_URI,
																		values,
																		FilterColumns._ID
																				+ '='
																				+ filterId,
																		null) > 0) {
																	cr.notifyChange(
																			FilterColumns
																					.FILTERS_FOR_FEED_CONTENT_URI(getIntent()
																							.getData()
																							.getLastPathSegment()),
																			null);
																}
															}
														}
													});
										}
									})
							.setNegativeButton(android.R.string.cancel, null)
							.show();
				}

				mode.finish(); // Action picked, so close the CAB
				return true;
			case R.id.menu_delete:
				final long filterId = mFiltersCursorAdapter
						.getItemId(mFiltersCursorAdapter.getSelectedFilter());
				new AlertDialog.Builder(EditFeedActivity.this)
						//
						.setIcon(android.R.drawable.ic_dialog_alert)
						//
						.setTitle(R.string.filter_delete_title)
						//
						.setMessage(R.string.question_delete_filter)
						//
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										EntriesCursorAdapter.newCachedThreadPool
												.submit(new Runnable() {
													public void run() {
														ContentResolver cr = getContentResolver();
														if (cr.delete(
																FilterColumns.CONTENT_URI,
																FilterColumns._ID
																		+ '='
																		+ filterId,
																null) > 0) {
															cr.notifyChange(
																	FilterColumns
																			.FILTERS_FOR_FEED_CONTENT_URI(getIntent()
																					.getData()
																					.getLastPathSegment()),
																	null);
														}
													}
												});
									}
								}).setNegativeButton(android.R.string.no, null)
						.show();

				mode.finish(); // Action picked, so close the CAB
				return true;
			default:
				return false;
			}
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mFiltersCursorAdapter.setSelectedFilter(-1);
			mFiltersListView.invalidateViews();
		}
	};

	public void onClickSearch(View view) {
		final View dialogView = getLayoutInflater().inflate(
				R.layout.search_feed, null);
		final EditText searchText = (EditText) dialogView
				.findViewById(R.id.searchText);

		new AlertDialog.Builder(EditFeedActivity.this)
				//
				.setIcon(R.drawable.action_search)
				//
				.setTitle(R.string.feed_search)
				//
				.setView(dialogView)
				//
				.setPositiveButton(android.R.string.search_go,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {

								String tmp = searchText.getText().toString();
								if (tmp == null || "".equals(tmp.trim()))
								{
									tmp = "";
								} else {
									try {
										tmp = URLEncoder.encode(searchText
												.getText().toString(),
												Constants.UTF8);
									} catch (UnsupportedEncodingException ignored) {
									}
								}
								final String text = tmp;
								final ProgressDialog pd = new ProgressDialog(
										EditFeedActivity.this);
								pd.setMessage(getString(R.string.loading));
								pd.setCancelable(true);
								pd.setIndeterminate(true);
								pd.show();

								getLoaderManager()
										.restartLoader(
												1,
												null,
												new LoaderCallbacks<ArrayList<HashMap<String, Object>>>() {

													@Override
													public Loader<ArrayList<HashMap<String, Object>>> onCreateLoader(
															int id, Bundle args) {
														return new GetFeedSearchResultsLoader(
																EditFeedActivity.this,
																text);
													}

													@Override
													public void onLoadFinished(
															Loader<ArrayList<HashMap<String, Object>>> loader,
															final ArrayList<HashMap<String, Object>> data) {
														pd.cancel();

														if (data == null) {
															Toast.makeText(
																	EditFeedActivity.this,
																	R.string.error,
																	Toast.LENGTH_SHORT)
																	.show();
														} else if (data
																.isEmpty()) {
															Toast.makeText(
																	EditFeedActivity.this,
																	R.string.no_result,
																	Toast.LENGTH_SHORT)
																	.show();
														} else {
															AlertDialog.Builder builder = new AlertDialog.Builder(
																	EditFeedActivity.this);
															builder.setTitle(R.string.feed_search);

															String[] from = new String[] {
																	FEED_SEARCH_TITLE,
																	FEED_SEARCH_DESC,
																	FEED_SEARCH_HOME };

															int[] to = new int[] {
																	android.R.id.text1,
																	android.R.id.text2,
																	android.R.id.home };

															SimpleAdapter adapter = new SimpleAdapter(
																	EditFeedActivity.this,
																	data,
																	R.layout.search_result_item,
																	from, to);
															adapter.setViewBinder(new ViewBinder() {
																public boolean setViewValue(
																		View view,
																		Object data,
																		String textRepresentation) {
																	if (view instanceof ImageView
																			&& data instanceof String && !GeneralUtil.isEmpty((String)data)) {
																		ImageView iv = (ImageView) view;
																		new AQuery(
																				iv)
																				.image(data
																						.toString(),
																						options);
																		return true;
																	} else
																		return false;
																}
															});
															builder.setAdapter(
																	adapter,
																	new DialogInterface.OnClickListener() {
																		@Override
																		public void onClick(
																				DialogInterface dialog,
																				int which) {
																			Object searchTitle = data
																					.get(which)
																					.get(FEED_SEARCH_TITLE);
																			Object searchUrl = data
																					.get(which)
																					.get(FEED_SEARCH_URL);
																			mNameEditText
																					.setText(searchTitle == null ? ""
																							: searchTitle
																									.toString());
																			mUrlEditText
																					.setText(searchUrl == null ? ""
																							: searchUrl
																									.toString());
																		}
																	});
															builder.show();
														}
													}

													@Override
													public void onLoaderReset(
															Loader<ArrayList<HashMap<String, Object>>> arg0) {

													}
												}).forceLoad();

							}
						}).setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}

/**
 * A custom Loader that loads feed search results from the google WS.
 */
class GetFeedSearchResultsLoader extends
		AsyncTaskLoader<ArrayList<HashMap<String, Object>>> {

	private final String mSearchText;

	public GetFeedSearchResultsLoader(Context context, String searchText) {
		super(context);
		mSearchText = searchText;
	}

	/**
	 * This is where the bulk of our work is done. This function is called in a
	 * background thread and should generate a new set of data to be published
	 * by the loader.
	 */
	@Override
	public ArrayList<HashMap<String, Object>> loadInBackground() {
		try {
			HttpURLConnection conn = NetworkUtils.setupConnection(GeneralUtil
					.getString(R.string.rss_source) + mSearchText);
			try {

				// Parse results
				final ArrayList<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();

				String jsonStr = new String(NetworkUtils.getBytes(NetworkUtils
						.getConnectionInputStream(conn)));

				JSONArray entries = new JSONArray(jsonStr);
				for (int i = 0; i < entries.length(); i++) {
					try {
						JSONObject entry = (JSONObject) entries.get(i);
						String url = entry
								.get(EditFeedActivity.FEED_SEARCH_URL)
								.toString();
						if (!GeneralUtil.isEmpty(url)) {
							HashMap<String, Object> map = new HashMap<String, Object>();
							map.put(EditFeedActivity.FEED_SEARCH_TITLE, entry.has(EditFeedActivity.FEED_SEARCH_TITLE) ? Html.fromHtml(entry.get(EditFeedActivity.FEED_SEARCH_TITLE).toString()).toString() : "");
							map.put(EditFeedActivity.FEED_SEARCH_URL,  url);
							map.put(EditFeedActivity.FEED_SEARCH_HOME, entry.has(EditFeedActivity.FEED_SEARCH_HOME) ? entry.get(EditFeedActivity.FEED_SEARCH_HOME) : "");
							map.put(EditFeedActivity.FEED_SEARCH_DESC, entry.has(EditFeedActivity.FEED_SEARCH_DESC) ? Html.fromHtml(entry.get(EditFeedActivity.FEED_SEARCH_DESC).toString()).toString() : "");

							results.add(map);
						}
					} catch (Exception ignored) {
						ignored.printStackTrace();
					}
				}

				return results;
			} finally {
				conn.disconnect();
			}
		} catch (Exception e) {
			return null;
		}
	}
}
