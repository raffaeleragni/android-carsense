package ki.carsense.dialogs;

import ki.carsense.R;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class RecordFilenameDialog extends Dialog
{
	public interface OKListener {void ok(String filename);}
	
	private OKListener oklistener;
	
	public RecordFilenameDialog(Context context, OKListener oklistener)
	{
		super(context);
		this.oklistener = oklistener;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.dialog_recordfilename);
		setTitle(R.string.dialog_recordfilename_title);
		
		Button btn = (Button) findViewById(R.id.dialog_rf_button);
		btn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				EditText txt = (EditText) findViewById(R.id.dialog_rf_edit);
				oklistener.ok(txt.getText().toString());
				RecordFilenameDialog.this.dismiss();
			}
		});
	}
}
