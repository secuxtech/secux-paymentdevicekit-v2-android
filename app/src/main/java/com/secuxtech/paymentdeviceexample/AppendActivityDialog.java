package com.secuxtech.paymentdeviceexample;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

public class AppendActivityDialog {

    private AlertDialog mAlertDialog;
    private View mLoadView;
    private Context mContext;
    private EditText mEditTextCoin, mEditTextToken, mEditTextIconSize, mEditTextType, mEditTextNumber;

    protected View.OnFocusChangeListener mViewFocusChangeListener = new View.OnFocusChangeListener(){
        @Override
        public void onFocusChange(View v, boolean hasFocus) {

            if (!hasFocus) {
                hideKeyboard(v);
            }
        }
    };

    public void show(Context context) {
        mContext = context;
        mAlertDialog = new AlertDialog.Builder(context).create();
        mLoadView = LayoutInflater.from(context).inflate(R.layout.dialog_append_activity_info_input, null);
        mAlertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mAlertDialog.setView(mLoadView, 0, 0, 0, 0);
        mAlertDialog.setCanceledOnTouchOutside(true);


        mEditTextCoin = mLoadView.findViewById(R.id.editText_append_coin);
        mEditTextCoin.setOnFocusChangeListener(mViewFocusChangeListener);

        mEditTextToken = mLoadView.findViewById(R.id.editText_append_token);
        mEditTextIconSize = mLoadView.findViewById(R.id.editText_append_icon_size);
        mEditTextType = mLoadView.findViewById(R.id.editText_append_type);
        mEditTextNumber = mLoadView.findViewById(R.id.editText_append_number);

        try {
            mAlertDialog.show();
        }catch (Exception e){
            e.printStackTrace();
            //LogHandler.Log("Show RefundConfirmDialog exception");
        }
    }

    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager)mContext.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager!=null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public String getCoin(){
        return mEditTextCoin.getText().toString();
    }
    public String getToken(){
        return mEditTextToken.getText().toString();
    }
    public Integer getIconSize(){
        return Integer.parseInt(mEditTextIconSize.getText().toString());
    }
    public Integer getType(){
        return Integer.parseInt(mEditTextType.getText().toString());
    }
    public Integer getNumber(){
        return Integer.parseInt(mEditTextNumber.getText().toString());
    }


    public void dismiss(){
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    public boolean isShown(){
        return mAlertDialog!=null;
    }

}
