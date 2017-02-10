package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

public class TrainActivity extends Activity {
    private static final String TAG = "TrainActivity";
    public static final String NAME_INTENT = "name";
    private static final String TOKEN = "7a5543f5ecd34419010c1233269d0d749d6db493";
    private Button train, reset;
    private ImageView record, tick1, tick2, tick3;
    private TextView instructions;
    private RecordWavMaster recorder = new RecordWavMaster();
    private ArrayList<File> samples = new ArrayList<>();
    private String name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.train_activity);

        record = (ImageView) findViewById(R.id.record_button);
        train = (Button) findViewById(R.id.train);
        reset = (Button) findViewById(R.id.reset);

        train.setEnabled(false);
        reset.setEnabled(false);

        instructions = (TextView) findViewById(R.id.record_name);

        Intent intent = getIntent();
        TextView instructions = (TextView) findViewById(R.id.record_name);
        name = intent.getExtras().getString(NAME_INTENT);
        instructions.setText(
                getString(R.string.record_instructions, name));

        tick1 = (ImageView) findViewById(R.id.tick1);
        tick2 = (ImageView) findViewById(R.id.tick2);
        tick3 = (ImageView) findViewById(R.id.tick3);

        record.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    recorder.recordWavStart();
                    record.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    endRecording();
                    record.setPressed(false);
                }
                return true;
            }
        });

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (File file: samples) {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete: " + file.toString());
                    }
                }
                samples = new ArrayList<>();
                updateButtons();
            }
        });

        train.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    final JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    JSONArray samplesArray = new JSONArray();
                    for (File file: samples) {
                        JSONObject sampleObj = new JSONObject();
                        sampleObj.put("wave", encodeFile(file));
                        samplesArray.put(sampleObj);
                    }
                    obj.put("voice_samples", samplesArray);
                    obj.put("token", TOKEN);
                    obj.put("microphone", "android microphone");
                    obj.put("language", "en");

                    StringEntity entity = new StringEntity(obj.toString());
                    entity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    AsyncHttpClient client = new AsyncHttpClient();

                    client.post(TrainActivity.this, "https://snowboy.kitt.ai/api/v1/train/", entity, "application/json", new AsyncHttpResponseHandler() {
                        @Override
                        public void onStart() {
                            Toast.makeText(TrainActivity.this, "Uploading samples.", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                            File output = new File(new File(Environment.getExternalStorageDirectory(), "pbrain"), name.replaceAll(" ", "_").toLowerCase() + ".pmdl");
                            try {
                                FileOutputStream stream = new FileOutputStream(output);
                                stream.write(response);
                                stream.close();
                                Log.i(TAG, "Training successful. Written to: " + output.toString());
                                Toast.makeText(TrainActivity.this, "Successfully trained.", Toast.LENGTH_SHORT).show();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent returnIntent = getIntent();
                                        setResult(RESULT_OK,returnIntent);
                                        finish();
                                    }
                                });
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to write trained data.", e);
                                Toast.makeText(TrainActivity.this, "Failed to write response.", Toast.LENGTH_LONG).show();
                            }

                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                            Toast.makeText(TrainActivity.this, "REST Request Failed.", Toast.LENGTH_LONG).show();
                            Log.e(TAG, new String(errorResponse));
                        }

                        @Override
                        public void onRetry(int retryNo) {
                            // called when request is retried
                        }
                    });
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Failed to create JSON object for training.", e);
                    Toast.makeText(TrainActivity.this, "Failed To Send Training Data", Toast.LENGTH_LONG).show();
                }

                for (File file: samples) {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete: " + file.toString());
                    }
                }
                samples = new ArrayList<>();
                updateButtons();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (File file: samples) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete: " + file.toString());
            }
        }
    }

    private void updateButtons() {
        train.setEnabled(false);
        reset.setEnabled(false);
        record.setEnabled(true);
        if (samples.size() > 0) {
            reset.setEnabled(true);
        }
        if (samples.size() > 2) {
            record.setEnabled(false);
            train.setEnabled(true);
        }
        switch (samples.size()) {
            case 0:
                instructions.setText(getString(R.string.record_instructions, name));
                break;
            case 1:
                instructions.setText(R.string.record_instructions1);
                break;
            case 2:
                instructions.setText(R.string.record_instructions2);
                break;
            case 3: // Fallthrough.
            default:
                instructions.setText(R.string.record_instructions3);
                break;
        }
        tick1.setPressed(samples.size() > 0);
        tick2.setPressed(samples.size() > 1);
        tick3.setPressed(samples.size() > 2);
    }

    private void endRecording() {
        String file = recorder.recordWavStop();
        if (file != null) {
            samples.add(new File(recorder.getFileName('/' + file)));
        }
        updateButtons();
    }

    private String encodeFile(File file) throws FileNotFoundException {
        InputStream inputStream = new FileInputStream(file);
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }
}
