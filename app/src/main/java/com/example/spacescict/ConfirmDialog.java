package com.example.spacescict;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

public class ConfirmDialog {
    public interface OnConfirmListener {
        void onConfirm();
    }

    public static void show(
            Context context,
            String title,
            String message,
            String confirmText,
            String cancelText,
            OnConfirmListener listener
    ) {

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);

        dialog.getWindow().setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );

        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialog.findViewById(R.id.dialogMessage);

        View btnConfirm = dialog.findViewById(R.id.btnConfirm);
        View btnCancel = dialog.findViewById(R.id.btnCancel);

        if (dialogTitle != null) dialogTitle.setText(title);
        if (dialogMessage != null) dialogMessage.setText(message);

        if (btnConfirm instanceof TextView) ((TextView) btnConfirm).setText(confirmText);
        if (btnCancel instanceof TextView) ((TextView) btnCancel).setText(cancelText);

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                listener.onConfirm();
                dialog.dismiss();
            });
        }

        dialog.show();
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.setCancelable(false);
    }
}
