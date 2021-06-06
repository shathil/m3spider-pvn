/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.mmmsys.m3vpn;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class M3VPNActivity extends ActionBarActivity
{
    private static final int VPN_REQUEST_CODE = 0x0F;

    private boolean waitingForVPNStart;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (M3VPNService.BROADCAST_VPN_STATE.equals(intent.getAction()))
            {
                if (intent.getBooleanExtra("running", false))
                    waitingForVPNStart = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_vpn);
        final Button vpnButton = (Button)findViewById(R.id.vpn);

        String addrs1 = "128.214.11.246::4445";
        String addrs2 = "128.214.11.246::4444";
        int toS1 = 142;
        int toS2 = 143;
        M3VPNConfig.getQueueInstance().push(toS1,addrs1);
        M3VPNConfig.getQueueInstance().push(toS2,addrs1);

        vpnButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startVPN();
            }
        });
        waitingForVPNStart = false;
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(M3VPNService.BROADCAST_VPN_STATE));
    }

    private void startVPN()
    {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
        {
            waitingForVPNStart = true;
            startService(new Intent(this, M3VPNService.class));
            enableButton(false);
        }
    }

    private class InstalledApplication extends AsyncTask<String, Void, String> {

        final PackageManager mmpkgManager = getApplicationContext().getPackageManager();
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);


        HashMap<Integer, String> idNames= new HashMap<Integer, String>();


        @Override
        protected String doInBackground(String... params) {
            List<ApplicationInfo> installedApps = mmpkgManager.getInstalledApplications(0);
            for (ApplicationInfo ai : installedApps) {
               // MetaMineConstants.idPackages.put(ai.uid,ai.packageName);
                String apnamr = ai.loadLabel(mmpkgManager).toString();
                idNames.put(ai.uid, apnamr);

            }
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            if(result.equals("Executed")) {
                //BroadcastMessageQueue.getQueueInstance().push(Integer.valueOf(0), "root");
               // MetaMineConstants.IdAppNameMaps.put(Integer.valueOf(0), "root");
                for (Map.Entry<Integer, String> entry : idNames.entrySet()) {
                    Integer id = entry.getKey();
                    String appName = entry.getValue();
                    Log.d("ApplicationId","id "+id+" appname "+appName);
                    //BroadcastMessageQueue.getQueueInstance().push(id, appName);
                   // MetaMineConstants.IdAppNameMaps.put(id, appName);
                }
                // this is for the root user.
            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }


    @Override
    protected void onResume() {
        super.onResume();

        enableButton(!waitingForVPNStart && !M3VPNService.isRunning());
    }

    private void enableButton(boolean enable)
    {
        final Button vpnButton = (Button) findViewById(R.id.vpn);
        if (enable)
        {
            vpnButton.setEnabled(true);
            vpnButton.setText(R.string.start_vpn);
        }
        else
        {
            vpnButton.setEnabled(false);
            vpnButton.setText(R.string.stop_vpn);
        }
    }
}
