package com.github.ivbaranov.rxbluetooth.example;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import com.github.ivbaranov.rxbluetooth.DiscoveryState;
import com.github.ivbaranov.rxbluetooth.RxBluetooth;
import java.util.ArrayList;
import java.util.List;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
  private Button start;
  private Button stop;
  private ListView result;
  private RxBluetooth rxBluetooth;
  private Subscription deviceSubscription;
  private Subscription discoveryStartSubscription;
  private Subscription discoveryFinishSubscription;
  private List<String> devices = new ArrayList<>();

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    start = (Button) findViewById(R.id.start);
    stop = (Button) findViewById(R.id.stop);
    result = (ListView) findViewById(R.id.result);

    rxBluetooth = new RxBluetooth();
    if (!rxBluetooth.isBluetoothEnabled()) {
      rxBluetooth.enableBluetooth(this);
    }

    deviceSubscription = rxBluetooth.observeDevices(this)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(Schedulers.io())
        .subscribe(new Action1<BluetoothDevice>() {
          @Override public void call(BluetoothDevice bluetoothDevice) {
            addDevice(bluetoothDevice);
          }
        });

    discoveryStartSubscription = rxBluetooth.observeDiscovery(this)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(Schedulers.io())
        .filter(DiscoveryState.isEqualTo(DiscoveryState.DISCOVERY_STARTED))
        .subscribe(new Action1<DiscoveryState>() {
          @Override public void call(DiscoveryState discoveryState) {
            start.setText(R.string.button_searching);
          }
        });

    discoveryFinishSubscription = rxBluetooth.observeDiscovery(this)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(Schedulers.io())
        .filter(DiscoveryState.isEqualTo(DiscoveryState.DISCOVERY_FINISHED))
        .subscribe(new Action1<DiscoveryState>() {
          @Override public void call(DiscoveryState discoveryState) {
            start.setText(R.string.button_restart);
          }
        });

    start.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        devices.clear();
        setAdapter(devices);
        rxBluetooth.startDiscovery();
      }
    });
    stop.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        rxBluetooth.cancelDiscovery();
      }
    });
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    if (rxBluetooth != null) {
      // Make sure we're not doing discovery anymore
      rxBluetooth.cancelDiscovery();
    }

    unsubscribe(deviceSubscription);
    unsubscribe(discoveryStartSubscription);
    unsubscribe(discoveryFinishSubscription);
  }

  private void addDevice(BluetoothDevice device) {
    String deviceName;
    deviceName = device.getAddress();
    if (!TextUtils.isEmpty(device.getName())) {
      deviceName += " " + device.getName();
    }
    devices.add(deviceName);

    setAdapter(devices);
  }

  private void setAdapter(List<String> list) {
    int itemLayoutId = android.R.layout.simple_list_item_1;
    result.setAdapter(new ArrayAdapter<>(this, itemLayoutId, list));
  }

  private static void unsubscribe(Subscription subscription) {
    if (subscription != null && !subscription.isUnsubscribed()) {
      subscription.unsubscribe();
      subscription = null;
    }
  }
}
