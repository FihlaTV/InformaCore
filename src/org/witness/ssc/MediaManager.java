package org.witness.ssc;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Keys.Media;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.io.Uploader;
import org.witness.mods.InformaButton;
import org.witness.mods.InformaHeaderTextView;
import org.witness.ssc.utils.MediaManagerAdapter;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.utils.Selections;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class MediaManager extends Activity implements OnClickListener {
	SharedPreferences sp;
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	ListView mediaList;
	ArrayList<Selections> media;
	int level = 0;
	
	ImageButton levelUp;
	InformaButton checkMessages, quit;
	JSONObject mediaObjectInContext = null;
	
	Uploader uploader;
	AlertDialog ab;
	
	private static final String TAG = InformaConstants.TAG;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.media_manager);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		mediaList = (ListView) findViewById(R.id.mediaList);
		
		media = new ArrayList<Selections>();
		if(getIntent().hasExtra(Keys.Media.Manager.SHARE_BASE)) {
			try {
				getList(getIntent().getLongArrayExtra(Keys.Media.Manager.SHARE_BASE));
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
			level = 1;
		} else {
			try {
				getList();
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
		}
		
		levelUp = (ImageButton) findViewById(R.id.mediaLevelUp);
		if(level == 1)
			levelUp.setImageResource(R.drawable.ic_level_up_inactive);
		else
			levelUp.setImageResource(R.drawable.ic_level_up);
		levelUp.setOnClickListener(this);
		
		checkMessages = (InformaButton) findViewById(R.id.mediaCheckMessages);
		checkMessages.setOnClickListener(this);
		
		quit = (InformaButton) findViewById(R.id.mediaQuit);
		quit.setOnClickListener(this);
		
		uploader = Uploader.getUploader();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		try {
			updateAlias();
		} catch (JSONException e) {}
		db.close();
		dh.close();
		sp = null;
	}
	
	private void updateAlias() throws JSONException {
		dh.setTable(db, Tables.IMAGES);
		for(Selections s: media) {
			ContentValues cv = new ContentValues();
			cv.put(Keys.Media.ALIAS, s.getExtras().getString(Keys.Media.ALIAS));
			
			db.update(dh.getTable(), cv, 
					Keys.Image.LOCATION_OF_OBSCURED_VERSION + " =?", 
					new String[] {s.getExtras().getString(Keys.Image.LOCATION_OF_OBSCURED_VERSION)});
		}
	}
	
	private void getList(long[] shareBase) throws JSONException {
		dh.setTable(db, Tables.IMAGES);
		Object[] sb = new Object[shareBase.length];
		int o = 0;
		for(long l : shareBase) {
			sb[o] = l;
			o++;
		}
		Cursor c = dh.getMultiple(db, new String[] {
				Keys.Image.LOCATION_OF_OBSCURED_VERSION,
				Keys.Media.ALIAS,
				Keys.Intent.Destination.EMAIL,
				Keys.Media.MEDIA_TYPE,
				Keys.Media.SHARE_VECTOR,
				Keys.TrustedDestinations.DESTO,
				Keys.Media.Manager.MESSAGE_URL}, BaseColumns._ID, sb);
		if(c != null && c.getCount() > 0) {
			populateList(c);
		}
	}
	
	private void getList() throws JSONException {
		dh.setTable(db, Tables.IMAGES);
		Cursor c = dh.getValue(db, new String[] {
				Keys.Image.LOCATION_OF_OBSCURED_VERSION,
				Keys.Media.ALIAS,
				Keys.Intent.Destination.EMAIL,
				Keys.Media.MEDIA_TYPE,
				Keys.Media.SHARE_VECTOR,
				Keys.TrustedDestinations.DESTO,
				Keys.Media.Manager.MESSAGE_URL}, null, null);
		if(c != null && c.getCount() > 0) {
			populateList(c);
		}
	}
	
	private void populateList(Cursor c) throws JSONException {
		c.moveToFirst();
		while(!c.isAfterLast()) {
			JSONObject details = new JSONObject();
			details.put(Keys.Image.LOCATION_OF_OBSCURED_VERSION, c.getString(c.getColumnIndex(Keys.Image.LOCATION_OF_OBSCURED_VERSION)));
			details.put(Keys.Intent.Destination.EMAIL, c.getString(c.getColumnIndex(Keys.Intent.Destination.EMAIL)));
			details.put(Keys.Media.MEDIA_TYPE, c.getInt(c.getColumnIndex(Keys.Media.MEDIA_TYPE)));
			details.put(Keys.Media.SHARE_VECTOR, c.getInt(c.getColumnIndex(Keys.Media.SHARE_VECTOR)));
			details.put(Keys.Media.Manager.MESSAGE_URL, c.getString(c.getColumnIndex(Keys.Media.Manager.MESSAGE_URL)));
			
			if(c.getString(c.getColumnIndex(Keys.TrustedDestinations.DESTO)) != null)
				details.put(Keys.TrustedDestinations.DESTO, c.getString(c.getColumnIndex(Keys.TrustedDestinations.DESTO)));
			
			String[] displayName = details.getString(Keys.Image.LOCATION_OF_OBSCURED_VERSION).split("/");
			if(c.getString(c.getColumnIndex(Keys.Media.ALIAS)) != null)
				details.put(Keys.Media.ALIAS, c.getString(c.getColumnIndex(Keys.Media.ALIAS)));
			else
				details.put(Keys.Media.ALIAS, displayName[displayName.length - 1]);
			media.add(new Selections(displayName[displayName.length - 1],false,details));
			
			c.moveToNext();
		}
		
		c.close();
		
		refreshList();
	}
	
	private void refreshList() {
		mediaList.setAdapter(new MediaManagerAdapter(this, media));
		registerForContextMenu(mediaList);
	}
	
	private void viewMedia() throws JSONException {
		Intent iView = new Intent(Intent.ACTION_VIEW);
		Uri viewMediaUri = Uri.fromFile(new File(mediaObjectInContext.getString(Keys.Image.LOCATION_OF_OBSCURED_VERSION)));
		switch(mediaObjectInContext.getInt(Media.MEDIA_TYPE)) {
		case MediaTypes.PHOTO:
			iView.setType(ObscuraConstants.MIME_TYPE_JPEG);
			iView.putExtra(Intent.EXTRA_STREAM, viewMediaUri);
			iView.setDataAndType(viewMediaUri, ObscuraConstants.MIME_TYPE_JPEG);
			break;
		case MediaTypes.VIDEO:
			iView.setDataAndType(viewMediaUri, ObscuraConstants.MIME_TYPE_MKV);
			break;
		}
		startActivity(iView);
    	finish();
    }
    
    
    private void shareMedia() throws JSONException {
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	switch(mediaObjectInContext.getInt(Media.MEDIA_TYPE)) {
		case InformaConstants.MediaTypes.PHOTO:
			intent.setType(ObscuraConstants.MIME_TYPE_JPEG);
			break;
		case InformaConstants.MediaTypes.VIDEO:
			intent.setType(ObscuraConstants.MIME_TYPE_MP4);
			break;
    	}
    	intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(mediaObjectInContext.getString(Keys.Image.LOCATION_OF_OBSCURED_VERSION))));
    	startActivity(Intent.createChooser(intent, getResources().getString(R.string.informaMediaManager_sharePrompt)));
    	finish();
    }
	
	private void getInfo() {
		
	}
	
	private void setAlias() throws JSONException {
		View aliasView = LayoutInflater.from(this).inflate(R.layout.message_prompt, null);
		AlertDialog.Builder aliasBuilder = new AlertDialog.Builder(this);
		aliasBuilder.setView(aliasView);
		
		InformaHeaderTextView header = (InformaHeaderTextView) aliasView.findViewById(R.id.messagePrompt_title);
		TextView prompt = (TextView) aliasView.findViewById(R.id.messagePrompt_summary);
		final EditText editField = (EditText) aliasView.findViewById(R.id.messagePrompt_text);
		InformaButton ok = (InformaButton) aliasView.findViewById(R.id.messagePrompt_ok);
		
		header.setText(getString(R.string.informaMediaManager_rename));
		prompt.setText(getString(R.string.informaMediaManager_rename) + " " + mediaObjectInContext.getString(Keys.Media.ALIAS));
		
		ok.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				try {
					mediaObjectInContext.put(Keys.Media.ALIAS, editField.getText().toString());
					ab.dismiss();
					refreshList();
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
			}
		});
		
		ab = aliasBuilder.create();
		ab.show();
	}
	
	private void viewMessages() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, JSONException {
		viewMessages(null, null, null);
	}
	
	private void viewMessages(String desto, String messageUrl, String keyHash) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, JSONException {
		JSONArray messages = new JSONArray();
		if(messageUrl != null) {
			messages.put(uploader.getMessages(desto, messageUrl, keyHash)); 
		} else {
			for(Selections s : media) {
				JSONObject extras = s.getExtras();
				if(extras.has(Keys.Media.Manager.MESSAGE_URL))
					messages.put(uploader.getMessages(extras.getString(Keys.TrustedDestinations.DESTO), extras.getString(Keys.Media.Manager.MESSAGE_URL), extras.getString(Keys.Media.KEY_HASH)));
			}
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		String[] menuItems = getResources().getStringArray(R.array.media_manager_context_menu);
		
		int i = 0;
		for(String s : menuItems) {
			menu.add(menu.NONE, i, i, s);
			i++;
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem mi) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) mi.getMenuInfo();
		mediaObjectInContext = media.get(info.position).getExtras();
		
		switch(mi.getItemId()) {
		case 0:
			try {
				shareMedia();
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
			return true;
		case 1:
			try {
				viewMedia();
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
			return true;
		case 2:
			try {
				setAlias();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return true;
		case 3:
			getInfo();
			return true;
		default:
			mediaObjectInContext = null;
			return false;
		}
	}
	
	private void levelUp() {
		level -= 0;
		if(level == 0)
			levelUp.setImageResource(R.drawable.ic_level_up_inactive);
		else
			levelUp.setImageResource(R.drawable.ic_level_up);
	}
	
	private void checkMessages() {
		
	}
	
	@Override
	public void onBackPressed() {
		if(level == 0)
			finish();
		else
			levelUp();
	}

	@Override
	public void onClick(View v) {
		if(v == levelUp && level != 0) {
			levelUp();
		} else if(v == checkMessages) {
			checkMessages();
		} else if(v == quit) {
			finish();
		}
		
	}
}
