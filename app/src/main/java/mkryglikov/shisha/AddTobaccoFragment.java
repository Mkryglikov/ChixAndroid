package mkryglikov.shisha;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import mkryglikov.data.adapters.AddItemAdapter;
import mkryglikov.data.models.TobaccosExtrasResponse;
import mkryglikov.data.RetrofitClient;
import pl.polak.clicknumberpicker.ClickNumberPickerView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddTobaccoFragment extends Fragment {
    private RecyclerView rvTobaccos;
    private ProgressBar pb;
    private String token;
    private AddItemAdapter tobaccosAdapter;
    private Account account;
    private AccountManager am;
    private AlertDialog.Builder adQuantity;
    private static LinkedList<String> tobaccosToSend = new LinkedList<String>();
    private List<TobaccosExtrasResponse> tobaccos;
    private Api api = RetrofitClient.getClient();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        am = AccountManager.get(getActivity());

        if (am.getAccountsByType(LoginActivity.ACCOUNT_TYPE).length > 0) {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED)
                return;
            account = am.getAccountsByType(LoginActivity.ACCOUNT_TYPE)[0];
            am.getAuthToken(account, LoginActivity.AUTH_TOKEN_TYPE, null, getActivity(), new OnTokenAcquired(), null);
        } else {
            am.addAccount(LoginActivity.ACCOUNT_TYPE, LoginActivity.AUTH_TOKEN_TYPE, null, null, getActivity(), null, null);
            getActivity().finish();
        }

        adQuantity = new AlertDialog.Builder(getActivity())
                .setTitle("Количество")
                .setView(R.layout.dialog_quantity)
                .setNegativeButton("Отмена", null)
                .setCancelable(true);

        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_add_tobacco, container, false);

        pb = (ProgressBar) rootView.findViewById(R.id.pb);
        rvTobaccos = (RecyclerView) rootView.findViewById(R.id.rvTobaccos);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rvTobaccos.setLayoutManager(llm);
        if (rvTobaccos.getAdapter() == null)
            rvTobaccos.setAdapter(tobaccosAdapter);
        rvTobaccos.addItemDecoration(new DividerItemDecoration(rvTobaccos.getContext(), llm.getOrientation()));
        ItemClickSupport.addTo(rvTobaccos).setOnItemClickListener(new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, final View v) {
                View viewAdQuantity = getActivity().getLayoutInflater().inflate(R.layout.dialog_quantity, null);
                final String tvNameText = ((TextView) v.findViewById(R.id.tvName)).getText().toString();
                final ClickNumberPickerView picker = (ClickNumberPickerView) viewAdQuantity.findViewById(R.id.quantityPicker);

                float currentQuantity = 0;
                for (String tobacco : tobaccosToSend) {
                    if (tobacco.equals(tvNameText))
                        currentQuantity++;
                }
                picker.setPickerValue(currentQuantity);

                adQuantity.setView(viewAdQuantity);
                adQuantity.setPositiveButton("Ок", new DialogInterface.OnClickListener() {//TODO жрет память
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ImageView ivAdd = (ImageView) v.findViewById(R.id.ivAdd);
                        ImageView ivEdit = (ImageView) v.findViewById(R.id.ivEdit);
                        ImageView ivAdded = (ImageView) v.findViewById(R.id.ivAdded);
                        TextView tvX = (TextView) v.findViewById(R.id.tvX);
                        TextView tvQuantity = (TextView) v.findViewById(R.id.tvQuantity);

                        Iterator<String> iter = tobaccosToSend.iterator();
                        while (iter.hasNext()) {
                            String s = iter.next();
                            if (s.equals(tvNameText)) {
                                iter.remove();
                            }
                        }

                        for (String tobacco : tobaccosToSend)
                            if (tobacco.equals(tvNameText)) tobaccosToSend.remove(tobacco);

                        for (int i = 0; i < Math.round(picker.getValue()); i++)
                            tobaccosToSend.add(tvNameText);

                        if (Math.round(picker.getValue()) == 0) {
                            ivEdit.setVisibility(View.GONE);
                            ivAdded.setVisibility(View.GONE);
                            tvX.setVisibility(View.GONE);
                            tvQuantity.setVisibility(View.GONE);
                            ivAdd.setVisibility(View.VISIBLE);
                        } else {
                            ivAdd.setVisibility(View.GONE);
                            ivEdit.setVisibility(View.VISIBLE);
                            tvX.setVisibility(View.VISIBLE);
                            tvQuantity.setVisibility(View.VISIBLE);
                            tvQuantity.setText(String.valueOf(Math.round(picker.getValue())));
                            ivAdded.setVisibility(View.VISIBLE);
                        }
                    }
                }).show();
            }
        });
        return rootView;
    }

    private void getTobaccos() {
        rvTobaccos.setVisibility(View.GONE);
        pb.setVisibility(View.VISIBLE);
        api.getTobaccos(LoginActivity.AUTH_TOKEN_TYPE + " " + token).enqueue(new Callback<List<TobaccosExtrasResponse>>() {
            @Override
            public void onResponse(Call<List<TobaccosExtrasResponse>> call, Response<List<TobaccosExtrasResponse>> response) {
                if (response.body() != null && response.code() == 200) {
                    tobaccos = response.body();
                    tobaccosAdapter = new AddItemAdapter((tobaccos));
                    rvTobaccos.setAdapter(tobaccosAdapter);
                    pb.setVisibility(View.GONE);
                    rvTobaccos.setVisibility(View.VISIBLE);
                    rvTobaccos.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fade_in));
                } else {//Todo 404 табаков нет
                    Log.d("FUCK", "Не получили список табаков " + response.code());
                    am.invalidateAuthToken(LoginActivity.ACCOUNT_TYPE, token);
                    am.getAuthToken(account, LoginActivity.AUTH_TOKEN_TYPE, null, getActivity(), new OnTokenAcquired(), null);
                }
            }

            @Override
            public void onFailure(Call<List<TobaccosExtrasResponse>> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private class OnTokenAcquired implements AccountManagerCallback<Bundle> {
        @Override
        public void run(AccountManagerFuture<Bundle> results) {
            try {
                token = results.getResult().getString(AccountManager.KEY_AUTHTOKEN);
            } catch (OperationCanceledException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthenticatorException e) {
                e.printStackTrace();
            }
            getTobaccos();
        }
    }

    public static String getTobaccosToSend() {
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for (String s : tobaccosToSend) {
            sb.append(prefix);
            prefix = ",";
            sb.append(s);
        }
        return sb.toString();
    }

    @Override
    public void onDestroy() {
        tobaccosToSend.clear();
        super.onDestroy();
    }
}
