package com.nawazishali.muetpractice;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditQuestionActivity extends Activity {

    private Db db;
    private long qid = -1; // -1 = new question

    private Spinner spSubject, spCorrect;
    private EditText etQuestion, etExplanation;
    private EditText[] etOptions = new EditText[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_question);
        db = new Db(this);
        qid = getIntent().getLongExtra("qid", -1);

        spSubject = findViewById(R.id.spSubject);
        spCorrect = findViewById(R.id.spCorrect);
        etQuestion = findViewById(R.id.etQuestion);
        etExplanation = findViewById(R.id.etExplanation);
        etOptions[0] = findViewById(R.id.etOpt0);
        etOptions[1] = findViewById(R.id.etOpt1);
        etOptions[2] = findViewById(R.id.etOpt2);
        etOptions[3] = findViewById(R.id.etOpt3);

        spSubject.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, Db.SUBJECTS) {
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
        spCorrect.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Option 1", "Option 2", "Option 3", "Option 4"}) {
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

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (qid > 0) {
            Question q = db.getQuestion(qid);
            if (q == null) {
                finish();
                return;
            }
            tvTitle.setText("Edit Question");
            int si = 0;
            for (int i = 0; i < Db.SUBJECTS.length; i++) {
                if (Db.SUBJECTS[i].equals(q.subject)) si = i;
            }
            spSubject.setSelection(si);
            etQuestion.setText(q.question);
            for (int i = 0; i < 4; i++) etOptions[i].setText(q.options[i]);
            spCorrect.setSelection(q.correctIndex);
            etExplanation.setText(q.explanation);
            View btnDelete = findViewById(R.id.btnDelete);
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete question?")
                    .setMessage("Ye sawal hamesha ke liye delete ho jayega.")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.deleteQuestion(qid);
                        Toast.makeText(this, "Question delete ho gaya", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        } else {
            tvTitle.setText("Add New Question");
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    private void save() {
        Question q = new Question();
        q.id = qid;
        if (qid > 0) {
            // Keep the existing chapter/topic when editing a question.
            Question old = db.getQuestion(qid);
            if (old != null) {
                q.chapter = old.chapter;
                q.topic = old.topic;
            }
        }
        q.subject = Db.SUBJECTS[spSubject.getSelectedItemPosition()];
        q.question = etQuestion.getText().toString().trim();
        for (int i = 0; i < 4; i++) q.options[i] = etOptions[i].getText().toString().trim();
        q.correctIndex = spCorrect.getSelectedItemPosition();
        q.explanation = etExplanation.getText().toString().trim();

        if (q.question.isEmpty() || q.explanation.isEmpty()
                || q.options[0].isEmpty() || q.options[1].isEmpty()
                || q.options[2].isEmpty() || q.options[3].isEmpty()) {
            Toast.makeText(this, "Saari fields bharo — sawal, 4 options aur explanation", Toast.LENGTH_LONG).show();
            return;
        }
        if (qid > 0) {
            db.updateQuestion(q);
            Toast.makeText(this, "Question update ho gaya", Toast.LENGTH_SHORT).show();
        } else {
            db.addQuestion(q);
            Toast.makeText(this, "Naya question add ho gaya", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
