package com.example.localmusic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
//import android.support.v7.widget.LinearLayoutManager;
//import android.support.v7.widget.RecyclerView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * 写于2023-01-08 周日清晨
 * */
public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final int REQUEST_CODE = 1;
    ImageView nextIv,playIv,lastIv,playstyleIv,image_loading;
    TextView singerTv,songTv;
    RecyclerView musicRv;
    private SeekBar seekBar;
    private boolean ischanging = false;
    Animation animation;
    private ObjectAnimator mCircleAnimator;
    int totalTime;

    private SearchView mSearchView;
    // 用于判断当前的播放顺序，0->单曲循环,1->顺序播放,2->随机播放
    private int play_style = 0;

    //数据源
    List<LocalMusicBean> mDatas ;
    private LocalMusicAdapter adapter;
    private int position;
    //记录当前音乐正在播放的位置
    int currentPlayPosition = -1;
    //记录赞停音乐时播放进度条位置
    int currentPausePositionInSong = 0;
    MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        mediaPlayer = new MediaPlayer();

        mDatas = new ArrayList<LocalMusicBean>();
        //new 适配器对象
        adapter = new LocalMusicAdapter(this,mDatas);
        musicRv.setAdapter(adapter);
        //RecyclerView可设置不同的显示方式网格、瀑布流  设置布局管理器
        LinearLayoutManager lineLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        musicRv.setLayoutManager(lineLayoutManager);

        //动态申请权限
        checkPermission();
        //加载本地数据源
        loadLocalMusicData();

        //设置每一项的点击事件
        setEventListener();

        playCompleteAutoStart();
    }

    //***********************************Handler原位置**************************************************

    private Handler handler=new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int currentPosition = msg.what;
            //Update positionBar
            seekBar.setProgress(currentPosition);
        }
    };

    private void playCompleteAutoStart() {
        //播放完毕后自动播放下一曲的方式************************************************************
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                switch (play_style){
                    case 0:
                        //播放当前播放的音乐
                        LocalMusicBean Bean1 = mDatas.get(currentPlayPosition);
                        playMusicInMusicBean(Bean1);
                        break;
                    case 1:
                        //自动播放下一首音乐
                        LocalMusicBean Bean2 = mDatas.get(currentPlayPosition+1);
                        playMusicInMusicBean(Bean2);
                        break;
                    case 2:
                        //随机播放下一首音乐
                        Random ran0=new Random();
                        int i=ran0.nextInt(5);
                        LocalMusicBean Bean3 = mDatas.get(i);
                        playMusicInMusicBean(Bean3);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void setEventListener() {
        adapter.setOnItemClickListener(new LocalMusicAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(View view, int position) {
                currentPlayPosition = position;
                LocalMusicBean musicBean = mDatas.get(position);
                playMusicInMusicBean(musicBean);
            }
        });
    }

    /**根据传入对象播放音乐*/
    public void playMusicInMusicBean(LocalMusicBean musicBean) {
        //设置底部显示的歌手名和歌曲名
        singerTv.setText(musicBean.getSinger());
        songTv.setText(musicBean.getSong());
        stopMusic();
        //重置多媒体播放器
        mediaPlayer.reset();
        seekBar.setProgress(0);
        try {
            mediaPlayer.setDataSource(musicBean.getPath());
            playMusic();
        } catch (IOException e) {
            e.printStackTrace(); //*******************************************************************************
        }
            totalTime=mediaPlayer.getDuration();
            seekBar.setMax(totalTime);

            //歌曲进程条变换
            seekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if(fromUser){
                                mediaPlayer.seekTo(progress);
                                seekBar.setProgress(progress);
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    });

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mediaPlayer !=null){
                        try {
                            Message msg=new Message();
                            msg.what=mediaPlayer.getCurrentPosition();
                            handler.sendMessage(msg);
                            Thread.sleep(1000);
                        }catch (InterruptedException e){}
                    }
                }
            }).start();
    }



    private void playMusic(){
        if(mediaPlayer != null &&!mediaPlayer.isPlaying()){
            if(currentPausePositionInSong == 0){
                //从头开始播放
                try {
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                //从暂停导播放
                mediaPlayer.seekTo(currentPausePositionInSong);
                mediaPlayer.start();
            }

            playIv.setImageResource(R.mipmap.icon_pause);
        }
    }

    private void stopMusic(){
        if(mediaPlayer != null){
            currentPausePositionInSong = 0;
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
            mediaPlayer.stop();
            playIv.setImageResource(R.mipmap.icon_play);
        }
    }

    private void pauseMusic(){
        if(mediaPlayer != null && mediaPlayer.isPlaying()){
            currentPausePositionInSong = mediaPlayer.getCurrentPosition();
            mediaPlayer.pause();
            playIv.setImageResource(R.mipmap.icon_play);

        }
    }
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限开启成功", Toast.LENGTH_LONG).show();
                loadLocalMusicData();                //加载本地数据源

            } else {
                Toast.makeText(this, "权限开启失败,无法获取本地音乐", Toast.LENGTH_LONG).show();
                return;
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMusic();
    }

    private void loadLocalMusicData() {
        //加载本地存储当中的音乐mp3文件到集合当中
        //1.获取ContentResolver对象
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        //3.查询地址
        Cursor cursor = resolver.query(uri,null,null,null,null);
        int id =0;
        while (cursor.moveToNext()){
            String song = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
            String singer = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
            String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
            id++;
            String sid = String.valueOf(id);
            String path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
            SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
            String time = sdf.format(new Date(duration));
            //讲一行中的数据封装到对象中
            LocalMusicBean bean = new LocalMusicBean(sid, song, singer, album, time, path);
            mDatas.add(bean);
        }

        //数据元更新
        adapter.notifyDataSetChanged();
    }

    private void initView() {
        nextIv = findViewById(R.id.local_music_bottom_iv_next);
        playIv = findViewById(R.id.local_music_bottom_iv_play);
        lastIv = findViewById(R.id.local_music_bottom_iv_last);
        singerTv = findViewById(R.id.local_music_bottom_iv_singer);
        songTv = findViewById(R.id.local_music_bottom_tv_song);
        musicRv = findViewById(R.id.local_music_rv);
        seekBar=(SeekBar)findViewById(R.id.local_music_bottom_iv_positionBar2);

        //******************************************************************************************
        image_loading =(ImageView) findViewById(R.id.local_music_bottom_iv_icon);
        //
        mCircleAnimator = ObjectAnimator.ofFloat(image_loading, "rotation", 0.0f, 360.0f);
        mCircleAnimator.setDuration(60000);
        mCircleAnimator.setInterpolator(new LinearInterpolator());
        mCircleAnimator.setRepeatCount(-1);
        mCircleAnimator.setRepeatMode(ObjectAnimator.RESTART);
        //*************************************************************************************
        //设置播放方式
        playstyleIv = findViewById(R.id.local_music_bottom_iv_playStyle);

        nextIv.setOnClickListener(this);
        playIv.setOnClickListener(this);
        lastIv.setOnClickListener(this);
        playstyleIv.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        Random ran1=new Random();
        switch (v.getId()){
            case R.id.local_music_bottom_iv_last:

                if(play_style==0){
                    LocalMusicBean lastBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(lastBean);
                    break;
                }else if(play_style==1){
                    if (currentPlayPosition==0) {
                        Toast.makeText(this,"已经是第一首了，即将从最后一首播放",Toast.LENGTH_SHORT).show();
                        //*****************************************************************************
                        currentPlayPosition=mDatas.size()-1;
                        LocalMusicBean lastBean = mDatas.get(currentPlayPosition);
                        playMusicInMusicBean(lastBean);
                        return;
                    }
                    currentPlayPosition = currentPlayPosition-1;
                    LocalMusicBean lastBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(lastBean);
                    break;
                }else {
                    currentPlayPosition=ran1.nextInt(5);
                    LocalMusicBean lastBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(lastBean);
                    break;
                }

            case R.id.local_music_bottom_iv_play:
                if(currentPlayPosition == -1){
                    Toast.makeText(this,"请选择要播放的音乐",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mediaPlayer.isPlaying()){
                    //处于播放状态，需要暂停音乐
                    pauseMusic();
                }else{
                    //此时没有播放音乐，点击下一首开始播放音乐
                    playMusic();
                }
                break;

            case R.id.local_music_bottom_iv_next:
                if(play_style==0)
                {
                    LocalMusicBean nextBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(nextBean);
                    break;
                }else if (play_style==1){
                    if (currentPlayPosition==mDatas.size()-1) {
                        Toast.makeText(this,"已经是最后一首了，即将从第一首播放",Toast.LENGTH_SHORT).show();
                        //**************************************************************************
                        currentPlayPosition=0;
                        LocalMusicBean nextBean = mDatas.get(currentPlayPosition);
                        playMusicInMusicBean(nextBean);
                        return;
                    }
                    currentPlayPosition = currentPlayPosition+1;
                    LocalMusicBean nextBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(nextBean);
                    break;
                }else {
                    currentPlayPosition = currentPlayPosition + ran1.nextInt(mDatas.size() - 1);
                    currentPlayPosition %= mDatas.size();
                    LocalMusicBean nextBean = mDatas.get(currentPlayPosition);
                    playMusicInMusicBean(nextBean);
                    break;
                }
            case R.id.local_music_bottom_iv_playStyle:
                play_style++;
                if (play_style > 2) {
                    play_style = 0;
                }
                if (play_style == 0){
                    playstyleIv.setImageResource(R.mipmap.icon_dan);
                    Toast.makeText(this, "单曲循环",
                            Toast.LENGTH_SHORT).show();
                }else if(play_style == 1){
                    playstyleIv.setImageResource(R.mipmap.icon_xun);
                    Toast.makeText(this, "顺序播放",
                            Toast.LENGTH_SHORT).show();
                }else if(play_style == 2 ){
                    playstyleIv.setImageResource(R.mipmap.icon_sui);
                    Toast.makeText(this, "随机播放",
                            Toast.LENGTH_SHORT).show();
                }
        }

    }
}