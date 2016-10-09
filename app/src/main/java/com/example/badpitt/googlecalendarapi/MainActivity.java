package com.example.badpitt.googlecalendarapi;

import android.databinding.DataBindingUtil;
import android.support.v7.app.AppCompatActivity;
import com.example.badpitt.googlecalendarapi.databinding.ActivityMainBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.CalendarScopes;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import rx.Subscriber;

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks
{
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};

    private GoogleAccountCredential credential;
    private ProgressDialog progress;

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        binding.getListButton.setOnClickListener(new OnCalendarButtonClickListener()
        {
            @Override
            public void newRequest()
            {
                getEventList();
            }
        });

        binding.addEventButton.setOnClickListener(new OnCalendarButtonClickListener()
        {
            @Override
            public void newRequest()
            {
                addNewEventRequest();
            }
        });

        progress = new ProgressDialog(this);
        progress.setMessage(getResources().getString(R.string.api_call));

        // Initialize credentials and service object.
        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                                            .setBackOff(new ExponentialBackOff());
    }

    private void getEventList()
    {
        CalendarRequestManager.getEventsListObservable(credential)
                              .subscribe(new CustomSubscriber<List<String>>()
                              {
                                  @Override
                                  public void onNext(List<String> s)
                                  {
                                      progress.hide();
                                      if (s == null || s.size() == 0)
                                      {
                                          binding.outputText.setText(
                                                  getResources().getString(R.string.no_result_returned));
                                      }
                                      else
                                      {
                                          s.add(0, "Data retrieved using the Google Calendar API:");
                                          binding.outputText.setText(TextUtils.join("\n", s));
                                      }
                                  }
                              });
    }

    private void addNewEventRequest()
    {
        CalendarRequestManager.getNewEventObservable(credential)
                              .subscribe(new CustomSubscriber<String>()
                              {
                                  @Override
                                  public void onNext(String s)
                                  {
                                      progress.hide();
                                      if (s == null || s.isEmpty())
                                      {
                                          binding.outputText.setText(
                                                  getResources().getString(R.string.no_result_returned));
                                      }
                                      else
                                      {
                                          binding.outputText.setText(s);
                                      }
                                  }
                              });
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount()
    {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS))
        {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null)
            {
                credential.setSelectedAccountName(accountName);
                getEventList();
            }
            else
            {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        credential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        }
        else
        {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK)
                {
                    binding.outputText.setText(getResources().getString(R.string.google_services_is_requires));
                }
                else
                {
                    getEventList();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK &&
                    data != null &&
                    data.getExtras() != null)
                {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

                    if (accountName != null)
                    {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        credential.setSelectedAccountName(accountName);
                        getEventList();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK)
                {
                    getEventList();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list)
    {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list)
    {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline()
    {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable()
    {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices()
    {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode))
        {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode)
    {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    private abstract class OnCalendarButtonClickListener implements Button.OnClickListener
    {
        @Override
        public void onClick(View view)
        {
            view.setEnabled(false);
            binding.outputText.setText("");

            if (!isGooglePlayServicesAvailable())
            {
                acquireGooglePlayServices();
            }
            else if (credential.getSelectedAccountName() == null)
            {
                chooseAccount();
            }
            else if (!isDeviceOnline())
            {
                binding.outputText.setText(getResources().getString(R.string.no_network));
            }
            else
            {
                newRequest();
            }

            view.setEnabled(true);
        }

        public abstract void newRequest();
    }

    private abstract class CustomSubscriber<T> extends Subscriber<T>
    {
        @Override
        public void onCompleted()
        {

        }

        @Override
        public void onError(Throwable e)
        {
            progress.hide();
            if (e != null)
            {
                if (e instanceof GooglePlayServicesAvailabilityIOException)
                {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) e)
                                    .getConnectionStatusCode());
                }
                else if (e instanceof UserRecoverableAuthIOException)
                {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) e).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                }
                else
                {
                    binding.outputText.setText(
                            String.format("%s%s",
                                          getResources().getString(R.string.following_error),
                                          e.getMessage()));
                }
            }
            else
            {
                binding.outputText.setText(getResources().getString(R.string.request_cancelled));
            }
        }

        @Override
        public void onStart()
        {
            super.onStart();
            binding.outputText.setText("");
            progress.show();
        }
    }
}