package com.example.spacescict;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.Button;
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

        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        dialogTitle.setText(title);
        dialogMessage.setText(message);

        btnConfirm.setText(confirmText);
        btnCancel.setText(cancelText);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            listener.onConfirm();
            dialog.dismiss();
        });

        dialog.show();
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.setCancelable(false);
    }
}
