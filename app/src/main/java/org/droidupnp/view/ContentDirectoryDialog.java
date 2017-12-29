package org.droidupnp.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.m3sv.droidupnp.R;

import org.droidupnp.controller.upnp.IUPnPServiceController;
import org.droidupnp.model.upnp.CallableContentDirectoryFilter;
import org.droidupnp.model.upnp.IUPnPDevice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

public class ContentDirectoryDialog extends DialogFragment {

    private Callable<Void> callback = null;
    IUPnPServiceController controller;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final Collection<IUPnPDevice> upnpDevices = controller.getServiceListener()
                .getFilteredDeviceList(new CallableContentDirectoryFilter());

        ArrayList<DeviceDisplay> list = new ArrayList<DeviceDisplay>();
        for (IUPnPDevice upnpDevice : upnpDevices)
            list.add(new DeviceDisplay(upnpDevice));

        ArrayAdapter<DeviceDisplay> rendererList = new ArrayAdapter<DeviceDisplay>(getActivity(),
                android.R.layout.simple_list_item_1, list);
        builder.setTitle(R.string.selectRenderer).setAdapter(rendererList, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                controller.setSelectedContentDirectory((IUPnPDevice) upnpDevices.toArray()[which]);
                try {
                    if (callback != null)
                        callback.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return builder.create();
    }

    public void setCallback(Callable<Void> callback) {
        this.callback = callback;
    }
}