package de.klarverwaltung.kv_ibuttonservice;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import de.klarverwaltung.kv_ibuttonservice.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        tv = binding.content;

        Intent serviceIntent = new Intent(getApplicationContext(), IButtonService.class);
        startService(serviceIntent);

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }

        IButtonEvent.getInstance().getLiveData().observe(this, event -> {
            tv.setText(event.content + " " + (event.returnCode == 46 ? "Aufgelegt" : "Abgezogen"));
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}