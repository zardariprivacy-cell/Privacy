package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class ManageActivity extends Activity {

    private Db db;
    private String filter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        db = new Db(this);

        User user = db.getUser(Session.currentUserId(this));
        if (user == null || !user.admin) {
            Toast.makeText(this, "Ye page sirf Admin ke liye hai", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add("All");
        for (String s : Db.SUBJECTS) filterOptions.add(s);
        Spinner sp = findViewById(R.id.spFilter);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, filterOptions) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                return v;
            }

            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getDropDownView(position, convertView, parent);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                v.setBackgroundColor(0xFF152849);
                return v;
            }
        });
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filter = filterOptions.get(position);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, EditQuestionActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        LinearLayout container = findViewById(R.id.listContainer);
        if (container == null) return;
        container.removeAllViews();
        List<Question> qs = filter.equals("All") ? db.allQuestions() : db.questionsFor(filter);
        for (Question q : qs) {
            TextView row = new TextView(this);
            row.setText(q.subject + " • " + q.topic + "\n" + q.question);
            row.setTextSize(15);
            row.setTextColor(0xFFFFFFFF);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, EditQuestionActivity.class);
                i.putExtra("qid", q.id);
                startActivity(i);
            });
            container.addView(row);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
