package com.chriszou.rxjavademo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.chriszou.rxjavademo.data.SearchUserResult;
import com.chriszou.rxjavademo.data.User;
import com.chriszou.rxjavademo.utils.Networker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxTextView;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by xiaochuang on 3/18/16.
 */
public class DiscoveryActivity extends AppCompatActivity {

    private LinearLayout usersLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.discovery_activity);

        usersLayout = (LinearLayout) findViewById(R.id.usersLayout);
        Button refrenshButton = (Button) findViewById(R.id.refresh);
        EditText searchBox = (EditText) findViewById(R.id.search_box);

        Observable<String> textChangedStream = RxTextView.afterTextChangeEvents(searchBox)
                .map(textChangedEvent -> textChangedEvent.editable().toString());
        Observable<String> searchTextEmptyStream = textChangedStream.filter(s -> s.length() == 0)
                .doOnNext(s -> {
                    refrenshButton.setVisibility(View.VISIBLE);
                    updateUserList(null);
                });
        Observable<Void> refreshStream = RxView.clicks(refrenshButton)
                .startWith((Void) null)
                .mergeWith(searchTextEmptyStream.map(s -> (Void) null));

        Observable<List<User>> recommandedUserStream = refreshStream
                .observeOn(Schedulers.io())
                .map(ignored -> getRecommendedUsers());

        Observable<String> searchButtonClickStream = RxView.clicks(findViewById(R.id.search_button))
                .map(ignored -> searchBox.getText().toString());

        Observable<String> searchOnTextChangedStream = textChangedStream.filter(s -> s.length() >= 3).debounce(500, TimeUnit.MILLISECONDS).distinctUntilChanged()
                .doOnNext(s -> Log.d("zyzy", "perform search on: " + s));

        Observable<List<User>> searchResultUserStream = Observable.merge(searchOnTextChangedStream, searchButtonClickStream.filter(s -> s.length() > 0))
                .observeOn(AndroidSchedulers.mainThread())
                .map(s -> {
                    refrenshButton.setVisibility(View.GONE);
                    updateUserList(null);
                    if (s.equals("error")) throw new RuntimeException("fail on purpose");
                    return s;
                })
                .lift(new OperatorSuppressError<>(Throwable::printStackTrace))
                .observeOn(Schedulers.io())
                .map(s -> searchUsers(s));
        searchResultUserStream.mergeWith(recommandedUserStream)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(users -> updateUserList((List<User>) users), throwable -> showError(throwable));
    }

    private void showError(Throwable e) {
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private List<User> searchUsers(String name) {
        Log.d("zyzy", "search users: " + name);
        try {
            String response = new Networker().get("https://api.github.com/search/users?q=" + name);
            SearchUserResult userResult = new Gson().fromJson(response, SearchUserResult.class);
            return userResult.users;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<User> getUsersFromJsonArray(String response) {
        Type listType = new TypeToken<List<User>>() {
        }.getType();
        return new Gson().fromJson(response, listType);
    }

    private void updateUserList(List<User> users) {
        usersLayout.removeAllViews();
        if (users == null) return;
        for (int i = 0; i < users.size(); i++) {
            usersLayout.addView(new UserView(this, users.get(i)));
        }
    }

    public List<User> getRecommendedUsers() {
        System.out.println("getting recommended users");

        int randomOffset = (int) Math.floor(Math.random() * 500);
        String url = "https://api.github.com/users?since=" + randomOffset;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        OkHttpClient client = new OkHttpClient();
        try {
            List<User> users = getUsersFromJsonArray(client.newCall(request).execute().body().string());
            Collections.shuffle(users);
            return users;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
    public static final class OperatorSuppressError<T> implements Operator<T, T> {
        final Action1<Throwable> onError;

        public OperatorSuppressError(Action1<Throwable> onError) {
            this.onError = onError;
        }

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            return new Subscriber<T>(t1) {

                @Override
                public void onNext(T t) {
                    t1.onNext(t);
                }

                @Override
                public void onError(Throwable e) {
                    onError.call(e);
                }

                @Override
                public void onCompleted() {
                    t1.onCompleted();
                }

            };
        }
    }


}
