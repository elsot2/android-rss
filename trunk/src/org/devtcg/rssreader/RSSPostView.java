/*
 * $Id$
 */

package org.devtcg.rssreader;

import java.util.Map;

import org.devtcg.rssprovider.RSSReader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ContentURI;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Menu.Item;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;

public class RSSPostView extends Activity
{
	private final static int NEXT_POST_ID = Menu.FIRST;
	private final static int PREV_POST_ID = Menu.FIRST + 1;
	
	private static final String[] PROJECTION = new String[] {
		RSSReader.Posts._ID, RSSReader.Posts.CHANNEL_ID,
		RSSReader.Posts.TITLE, RSSReader.Posts.BODY, RSSReader.Posts.READ,
		RSSReader.Posts.URL };
	
	private long mChannelID = -1;
	private long mPostID = -1;
	
	private Cursor mCursor;
	
	private long mPrevPostID = -1;
	private long mNextPostID = -1;
	
	@Override
	protected void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);		
		setContentView(R.layout.post_view);

		mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null, null);
		
		if (mCursor.first() == false)
			finish();
		
		mChannelID = mCursor.getLong(mCursor.getColumnIndex(RSSReader.Posts.CHANNEL_ID));
		mPostID = new Long(getIntent().getData().getPathSegment(1));

		/* TODO: Should this be in onStart() or onResume() or something?  */
		initWithData();
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();

		/* Set the post to read. */
		mCursor.updateInt(mCursor.getColumnIndex(RSSReader.Posts.READ), 1);
		mCursor.commitUpdates();
	}
	
	private void initWithData()
	{	
		ContentResolver cr = getContentResolver();

		Cursor cChannel = cr.query(RSSReader.Channels.CONTENT_URI.addId(mChannelID),
		  new String[] { RSSReader.Channels.ICON, RSSReader.Channels.LOGO, RSSReader.Channels.TITLE }, null, null, null);

		assert(cChannel.count() == 1);
		cChannel.first();
		
		/* Make the view useful. */
		RSSChannelHead head = (RSSChannelHead)findViewById(R.id.postViewHead);
		head.setLogo(cChannel);
		
		cChannel.close();

		TextView postTitle = (TextView)findViewById(R.id.postTitle);
		postTitle.setText(mCursor, mCursor.getColumnIndex(RSSReader.Posts.TITLE));

		WebView postText = (WebView)findViewById(R.id.postText);

		/* TODO: I want the background transparent, but that doesn't seem 
		 * possible.  Black will do for now. */
		String html =
			"<html><head><style type=\"text/css\">body { background-color: #201c19; color: white; } a { color: #ddf; }</style></head><body>" +
			getBody() +
			"</body></html>";

		postText.loadData(html, "text/html", "utf-8");
	}
	
	/* Apply some simple heuristics to the post text to determine what special
	 * features we want to show. */
	private String getBody()
	{
		String body =
		  mCursor.getString(mCursor.getColumnIndex(RSSReader.Posts.BODY));
		
		String url =
		  mCursor.getString(mCursor.getColumnIndex(RSSReader.Posts.URL));
	
		if (hasMoreLink(body, url) == false)
			body += "<p><a href=\"" + url + "\">Read more...</a></p>";
		
		/* TODO: We should add a check for "posted by", "written by",
		 * "posted on", etc, and optionally add our own tagline if
		 * the information is in the feed. */
		return body;
	}
	
	private boolean hasMoreLink(String body, String url)
	{
		int urlpos;
		
		/* Check if the body contains an anchor reference with the
		 * destination of the read more URL we got from the feed. */
		if ((urlpos = body.indexOf(url)) > 0)
		{
			try
			{
				/* TODO: Improve this check with a full look-behind parse. */
				if (body.charAt(urlpos - 1) != '>')
					return false;
			
				if (body.charAt(urlpos + url.length() + 1) != '<')
					return false;
			}
			catch (IndexOutOfBoundsException e)
			{
				return false;
			}
			
			return true;
		}
		
		return false;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		menu.removeGroup(0);
		
		if (mNextPostID < 0 || mPrevPostID < 0)
		{
	    	Cursor cPostList = getContentResolver().query
	    	 (RSSReader.Posts.CONTENT_URI_LIST.addId(mChannelID),
	    	  new String[] { RSSReader.Posts._ID }, null, null, null);

	    	/* TODO: This is super lame; we need to use SQLite queries to
	    	 * determine posts either newer or older than the current one
	    	 * without. */
	    	cPostList.first();
	    	
	    	int indexId = cPostList.getColumnIndex(RSSReader.Posts._ID);
	    	
	    	long lastId = -1;
	    	
	    	for (cPostList.first(); cPostList.isLast() == false; cPostList.next())
	    	{
	    		long thisId = cPostList.getLong(indexId);
	    		
	    		if (thisId == mPostID)
	    			break;
	    		
	    		lastId = thisId;
	    	}

	    	/* Remember, the order is descending by date. */
	    	if (mNextPostID < 0)
	    		mNextPostID = lastId;

	    	if (mPrevPostID < 0)
	    	{
	    		if (cPostList.isLast() == false)
	    		{
	    			cPostList.next();
	    			mPrevPostID = cPostList.getLong(indexId);
	    		}
	    	}
		}
		
		if (mNextPostID >= 0)
		{
			menu.add(0, NEXT_POST_ID, "Newer Post").
  	  	  	  setShortcut(KeyEvent.KEYCODE_3, 0, KeyEvent.KEYCODE_LEFT_BRACKET);
		}
    	
		if (mPrevPostID >= 0)
		{
			menu.add(0, PREV_POST_ID, "Older Post").
			  setShortcut(KeyEvent.KEYCODE_1, 0, KeyEvent.KEYCODE_RIGHT_BRACKET);
		}
		
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(Menu.Item item)
    {
    	ContentURI uri = RSSReader.Posts.CONTENT_URI;
    	
    	int itemId = item.getId();
    	
    	if (itemId == NEXT_POST_ID || itemId == PREV_POST_ID)
    	{
    		long postId;
    		
    		if (itemId == NEXT_POST_ID)
    			postId = mNextPostID;
    		else
    			postId = mPrevPostID;
    		
    		Intent intent = new Intent(Intent.VIEW_ACTION, uri.addId(postId));
    		startActivity(intent);
    		
    		/* Assume that user would do not want to keep the [now read]
    		 * current post in the history stack. */
    		finish();
    		
    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }

    /* Special ScrollView class that is used to determine if the post title
     * is currently visible.  If not, it will change it to show the
     * RSSChannelHead to show the title with a flashy animation to show
     * off Android goodness. */
    public class PostScrollView extends ScrollView
    {
    	public PostScrollView(Context context)
    	{
    		super(context);
    	}

    	public PostScrollView(Context context, AttributeSet attrs, Map inflateParams)
    	{
    		super(context, attrs, inflateParams);
    	}

    	public PostScrollView(Context context, AttributeSet attrs, Map inflateParams, int defStyle)
    	{
    		super(context, attrs, inflateParams, defStyle);
    	}

    	public void computeScroll()
    	{
    		super.computeScroll();
    		Log.d("RSSPostScrollView", "x=" + mScrollX + ", y=" + mScrollY);
    	}
    }
}
