package com.example.mynavigation.drawer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mynavigation.drawer.databinding.ActivityMainBinding;
import com.example.mynavigation.drawer.ui.home.HomeFragment;
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;
    private MenuItem newChatItem;
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_history, R.id.nav_settings)
                .setOpenableLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // 选择菜单项后自动关闭抽屉
        navigationView.setNavigationItemSelectedListener(item -> {
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) {
                drawer.closeDrawer(androidx.core.view.GravityCompat.START);
            }
            return handled;
        });

        // 点击 GitHub 跳转项目地址
        findViewById(R.id.nav_footer_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.baidu.com"));
            startActivity(intent);
            drawer.closeDrawer(androidx.core.view.GravityCompat.START);
        });

        // 双击返回退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != R.id.nav_home) {
                    navController.navigate(R.id.nav_home);
                    return;
                }
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBackPressTime < 2000) {
                    saveCurrentOnExit();
                    finish();
                } else {
                    lastBackPressTime = currentTime;
                    Toast.makeText(MainActivity.this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    private void saveCurrentOnExit() {
        Fragment navHost = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHost != null) {
            Fragment current = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
            if (current instanceof HomeFragment) {
                ((HomeFragment) current).saveSessionOnExit();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        newChatItem = menu.findItem(R.id.action_new_chat);
        if (navController != null && navController.getCurrentDestination() != null) {
            newChatItem.setVisible(navController.getCurrentDestination().getId() == R.id.nav_home);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_new_chat) {
            Fragment navHost = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
            if (navHost != null) {
                Fragment current = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
                if (current instanceof HomeFragment) {
                    ((HomeFragment) current).startNewChat();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}