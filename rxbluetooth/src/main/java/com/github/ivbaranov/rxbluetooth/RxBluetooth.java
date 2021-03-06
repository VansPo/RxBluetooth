/*
 * Copyright (C) 2015 Ivan Baranov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ivbaranov.rxbluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.text.TextUtils;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * Enables clients to listen to bluetooth events using RxJava Observables.
 */
public class RxBluetooth {
  public static final int REQUEST_ENABLE_BT = 62884;

  private BluetoothAdapter mBluetoothAdapter;

  public RxBluetooth() {
    this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  }

  /**
   * Returns local Bluetooth adapter.
   *
   * @return the default local adapter, or null if Bluetooth is not supported
   * on this hardware platform
   */
  public BluetoothAdapter getBluetoothAdapter() {
    return mBluetoothAdapter;
  }

  /**
   * Return true if Bluetooth is available.
   *
   * @return true if mBluetoothAdapter is not null or it's address is empty, otherwise Bluetooth is
   * not supported on this hardware platform
   */
  public boolean isBluetoothAvailable() {
    return !(mBluetoothAdapter == null || TextUtils.isEmpty(mBluetoothAdapter.getAddress()));
  }

  /**
   * Return true if Bluetooth is currently enabled and ready for use.
   * <p>Equivalent to:
   * <code>getBluetoothState() == STATE_ON</code>
   * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
   *
   * @return true if the local adapter is turned on
   */
  public boolean isBluetoothEnabled() {
    return mBluetoothAdapter.isEnabled();
  }

  /**
   * This will issue a request to enable Bluetooth through the system settings (without stopping
   * your application) via ACTION_REQUEST_ENABLE action Intent.
   *
   * @param activity Activity
   */
  public void enableBluetooth(Activity activity) {
    if (!mBluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
  }

  /**
   * This will issue a request to enable Bluetooth through the system settings (without stopping
   * your application) via ACTION_REQUEST_ENABLE action Intent.
   *
   * @param activity Activity
   * @param requestCode custom request code. Note: RequestCodes can only be a max of 0xffff (65535)
   */
  public void enableBluetooth(Activity activity, int requestCode) {
    if (!mBluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      activity.startActivityForResult(enableBtIntent, requestCode);
    }
  }

  /**
   * Start the remote device discovery process.
   */
  public boolean startDiscovery() {
    return mBluetoothAdapter.startDiscovery();
  }

  /**
   * Return true if the local Bluetooth adapter is currently in the device
   * discovery process.
   *
   * @return true if discovering
   */
  public boolean isDiscovering() {
    return mBluetoothAdapter.isDiscovering();
  }

  /**
   * Cancel the current device discovery process.
   *
   * @return true on success, false on error
   */
  public boolean cancelDiscovery() {
    return mBluetoothAdapter.cancelDiscovery();
  }

  /**
   * Observes Bluetooth devices found while discovering.
   *
   * @param context Context of the activity or an application
   * @return RxJava Observable with BluetoothDevice found
   */
  public Observable<BluetoothDevice> observeDevices(final Context context) {
    final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);

    return Observable.create(new Observable.OnSubscribe<BluetoothDevice>() {

      @Override public void call(final Subscriber<? super BluetoothDevice> subscriber) {
        final BroadcastReceiver receiver = new BroadcastReceiver() {
          @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
              BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
              subscriber.onNext(device);
            }
          }
        };

        context.registerReceiver(receiver, filter);

        subscriber.add(unsubscribeInUiThread(new Action0() {
          @Override public void call() {
            context.unregisterReceiver(receiver);
          }
        }));
      }
    });
  }

  /**
   * Observes DiscoveryState,
   * which can be DISCOVERY_STARTED or DISCOVERY_FINISHED
   *
   * @param context Context of the activity or an application
   * @return RxJava Observable with DiscoveryState
   */
  public Observable<DiscoveryState> observeDiscovery(final Context context) {
    final IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

    return Observable.create(new Observable.OnSubscribe<DiscoveryState>() {

      @Override public void call(final Subscriber<? super DiscoveryState> subscriber) {
        final BroadcastReceiver receiver = new BroadcastReceiver() {
          @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
              case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                subscriber.onNext(DiscoveryState.DISCOVERY_STARTED);
                break;
              case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                subscriber.onNext(DiscoveryState.DISCOVERY_FINISHED);
                break;
            }
          }
        };

        context.registerReceiver(receiver, filter);

        subscriber.add(unsubscribeInUiThread(new Action0() {
          @Override public void call() {
            context.unregisterReceiver(receiver);
          }
        }));
      }
    });
  }

  private Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
    return Subscriptions.create(new Action0() {

      @Override public void call() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
          unsubscribe.call();
        } else {
          final Scheduler.Worker inner = AndroidSchedulers.mainThread().createWorker();
          inner.schedule(new Action0() {
            @Override public void call() {
              unsubscribe.call();
              inner.unsubscribe();
            }
          });
        }
      }
    });
  }
}
