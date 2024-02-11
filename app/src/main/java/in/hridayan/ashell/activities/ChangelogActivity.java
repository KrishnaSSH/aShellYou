package in.hridayan.ashell.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import in.hridayan.ashell.R;
import in.hridayan.ashell.adapters.ChangelogAdapter;
import in.hridayan.ashell.utils.ChangelogItem;
import java.util.ArrayList;
import java.util.List;

public class ChangelogActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_changelog);

    int statusBarColor = getResources().getColor(R.color.StatusBar);
    double brightness = getBrightness(statusBarColor);
    boolean isLightStatusBar = brightness > 0.5;

    View decorView = getWindow().getDecorView();
    if (isLightStatusBar) {
      decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    } else {
      decorView.setSystemUiVisibility(0);
    }

    ImageView imageView = findViewById(R.id.arrow_back);

    imageView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {

            onBackPressed();
          }
        });

    RecyclerView recyclerViewChangelogs = findViewById(R.id.recycler_view_changelogs);

    List<ChangelogItem> changelogItems = new ArrayList<>();

    changelogItems.add(
        new ChangelogItem(
            "Version 1.3.1",
            "\n\n• Added predictive back animations.\n\n• Added confirmation dialog popup before clearing screen.\n\n• Fixed StatusBar elements not properly visible in Light mode."));
    changelogItems.add(
        new ChangelogItem(
            "Version 1.3.0",
            "\n\n• Revamped Settings panel.\n\n• Added Double Tap to instant scroll to top and bottom positions in shell output view.\n\n• Minor UI fixes."));
    changelogItems.add(
        new ChangelogItem(
            "Version 1.2.0",
            "\n\n• Added Settings panel.\n\n• Added option to disable smooth scroll in shell output.\n\n• Fixed major bugs :\n\n    ~ Crash while changing device theme.\n\n    ~ Crash while trying to save large shell output . eg : output of 'pm' command."));
    changelogItems.add(
        new ChangelogItem(
            "Version 1.1.1", "\n\n• Replaced some deprecated api.\n\n• Minor UI changes."));
    changelogItems.add(
        new ChangelogItem(
            "Version 1.1.0",
            "\n\n• Added Scroll To Top and Scroll To Bottom buttons.\n\n• Added Click keyboard Enter key to Send command.\n\n• Added some commands.\n\n• Updated dependencies to include latest material you library.\n\n• Minor UI changes."));
    changelogItems.add(
        new ChangelogItem(
            "Version 1.0.0",
            "\n\n• Added some more command examples.\n\n• Minor UI changes.\n\n• Changed package name to avoid version confusion with original app."));
    changelogItems.add(
        new ChangelogItem(
            "Version 0.9.1",
            "\n\n• Added changelogs into the app.\n\n• Added highlighted shell output.\n\n• Minor UI changes."));
    changelogItems.add(
        new ChangelogItem(
            "Version 0.9",
            "\n\n• Added dynamic material theming.\n\n• Revamped whole UI to give a fresh look."));

    ChangelogAdapter adapter = new ChangelogAdapter(changelogItems, this);
    recyclerViewChangelogs.setAdapter(adapter);
    recyclerViewChangelogs.setLayoutManager(new LinearLayoutManager(this));
  }

  public double getBrightness(int color) {
    int red = Color.red(color);
    int green = Color.green(color);
    int blue = Color.blue(color);
    return 0.299 * red + 0.587 * green + 0.114 * blue;
  }
}
