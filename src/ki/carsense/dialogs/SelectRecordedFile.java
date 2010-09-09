package ki.carsense.dialogs;

import java.io.File;
import java.io.FilenameFilter;

import ki.carsense.R;
import ki.carsense.activity.RecordActivity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class SelectRecordedFile extends ListActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		updateList();
		
		getListView().setOnItemClickListener(new OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id)
			{
				Object o = getListView().getAdapter().getItem(pos);
				Intent data = new Intent();
				data.putExtra("filename", o.toString());
				setResult(RESULT_OK, data);
				finish();
			}
		});
		
		getListView().setOnCreateContextMenuListener(new OnCreateContextMenuListener()
		{
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
			{
				MenuInflater mi = getMenuInflater();
				mi.inflate(R.menu.dialog_select_file_contextual, menu);
			}
		});
	}
	
	public void updateList()
	{
		File dir = new File(Environment.getExternalStorageDirectory()+"/carsense/");
		String[] l = dir.list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String filename)
			{
				return filename.endsWith(".cs");
			}
		});
		l = l == null ? new String[]{} : l;
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.player_select_file, l);
		setListAdapter(adapter);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
    		case R.id.dialog_select_file_menu_delete:
    		{
    	    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	    	String name = (String) getListView().getAdapter().getItem(info.position);
    	    	File f = new File(Environment.getExternalStorageDirectory() + "/carsense/", name);
    	    	File f2 = new File(Environment.getExternalStorageDirectory() + "/carsense/", name+"."+RecordActivity.VIDEO_EXTENSION);
    	    	if (f.exists())
    	    	{
    	    		f.delete();
    	    		if (f2.exists()) f2.delete();
    	    		updateList();
    	    	}
    			break;
    		}
		}

    	return super.onContextItemSelected(item);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
	}
}
