package com.example.musicplayer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.example.musicplayer.db.MusicPlayerDAO;
import com.example.musicplayer.fragment.AlbumListFragment;
import com.example.musicplayer.fragment.ArtistListFragment;
import com.example.musicplayer.fragment.MainFragment;
import com.example.musicplayer.fragment.MusicListFragment;
import com.example.musicplayer.message.Message;
import com.example.musicplayer.message.MessageCallback;
import com.example.musicplayer.message.MessageData2;
import com.example.musicplayer.message.MessagePump;
import com.example.musicplayer.pojo.Song;
import com.example.musicplayer.pojo.SongGroup;
import com.example.musicplayer.service.MusicPlayerService;
import com.example.musicplayer.service.MusicPlayerServiceBinder;
import com.example.musicplayer.util.TaskExecutor;
import com.example.musicplayer.util.Util;

import java.io.File;
import java.util.List;

public class MainActivity extends FragmentActivity implements View.OnClickListener, MessageCallback {
    private final static boolean DEBUG = true;
    private final static String TAG = MainActivity.class.getSimpleName();

    private MusicPlayerApplication mApp;
    private MusicPlayerDAO mMusicPlayerDAO;

    private MainFragment mMainFragment;
    private MusicListFragment mMusicListFragment;
    private ArtistListFragment mArtistListFragment;
    private AlbumListFragment mAlbumListFragment;

    private MusicPlayerService mMusicPlayerService;

    private MessagePump mMessagePump;

    private boolean mDestroyed;

    private TextView mCurSongTitle;
    private TextView mCurArtist;
    private TextView mCurSongProgress;
    private TextView mCurSongDuration;

    private ImageButton mBtnPlayAndPause;

    private ServiceConnection mServiceConnection;

    private SharedPreferences mPrefs;

    public final static String LIST_TYPE = "list_type";
    public final static String EXTRA_ID = "extra_id";
    public final static String EXTRA_TITLE = "extra_title";
    public final static int TYPE_ALL_MUSIC = 0;
    public final static int TYPE_BY_ARTIST = 1;
    public final static int TYPE_BY_ALBUM = 2;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(MusicPlayerApplication.SHARED_PREF, MODE_PRIVATE);

        mApp = (MusicPlayerApplication)getApplication();
        mMusicPlayerDAO = mApp.getMusicPlayerDAO();
        mMessagePump = mApp.getMessagePump();

        initFragments();
        showFragment(mMainFragment);

        mBtnPlayAndPause = (ImageButton) findViewById(R.id.btn_play_and_pause);
        mBtnPlayAndPause.setOnClickListener(this);
        findViewById(R.id.btn_play_prev_song).setOnClickListener(this);
        findViewById(R.id.btn_play_next_song).setOnClickListener(this);

        mCurSongTitle = (TextView) findViewById(R.id.tv_song_title);
        mCurSongTitle.getPaint().setFakeBoldText(true);
        mCurArtist = (TextView) findViewById(R.id.tv_artist);
        mCurSongProgress = (TextView) findViewById(R.id.tv_song_progress);
        mCurSongDuration = (TextView) findViewById(R.id.tv_song_duration);

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mMusicPlayerService = ((MusicPlayerServiceBinder) service).getMusicPlayerService();

                boolean isPlaying = mMusicPlayerService.isPlayingSong();
                mBtnPlayAndPause.setImageResource(isPlaying ? R.drawable.icon_pause_selector : R.drawable.icon_play_selector);

                Song currentSong = mMusicPlayerService.getCurrentSong();
                if (currentSong != null) {
                    setInfoForCurSong(currentSong, mMusicPlayerService.getPlayingProgress());
                } else {
                    final int lastPlayedSongId = mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, 0);
                    if (lastPlayedSongId > 0) {
                        TaskExecutor.executeTask(new Runnable() {
                            @Override
                            public void run() {
                                final Song song = mMusicPlayerDAO.getSongById(lastPlayedSongId);
                                if (song != null) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            setInfoForCurSong(song, mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_PROGRESS, 0));
                                        }
                                    });
                                }
                            }
                        });
                    }

                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Intent intent = new Intent(this, MusicPlayerService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);

        registerMessageCallbacks();
	}

    private void registerMessageCallbacks() {
        mMessagePump.register(Message.Type.ON_START_PLAYBACK, this);
        mMessagePump.register(Message.Type.ON_RESUME_PLAYBACK, this);
        mMessagePump.register(Message.Type.ON_PAUSE_PLAYBACK, this);
        mMessagePump.register(Message.Type.ON_UPDATE_PLAYING_PROGRESS, this);

        mMessagePump.register(Message.Type.SHOW_FRAGMENT_MUSIC_LIST, this);
        mMessagePump.register(Message.Type.SHOW_FRAGMENT_ARTIST_LIST, this);
        mMessagePump.register(Message.Type.SHOW_FRAGMENT_ALBUM_LIST, this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
    }

    private void initFragments () {
        mMainFragment = new MainFragment();

        mMusicListFragment = new MusicListFragment();
        mMusicListFragment.setArguments(new Bundle());

        mArtistListFragment = new ArtistListFragment();
        mAlbumListFragment = new AlbumListFragment();
    }

    private void showFragment(Fragment fragment) {
        if (mDestroyed)
            return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (fragment == mMainFragment) {
            ft.add(R.id.container, fragment);
        } else {
            ft.replace(R.id.container, fragment);
            ft.addToBackStack(null);
            ft.setTransition(FragmentTransaction.TRANSIT_ENTER_MASK);
        }

        ft.commitAllowingStateLoss();
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.action_scan:
                Toast.makeText(this, "开始扫描SD卡...", Toast.LENGTH_SHORT).show();
                TaskExecutor.executeTask(new Runnable() {
                    @Override
                    public void run() {
                        scanMP3Files();

                        mMainFragment.setItemDataCounts(true);

                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "扫描完成！", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
                break;
		case R.id.action_quit:
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			break;
		}
		return super.onKeyDown(keyCode, event);
	}


    private void scanMP3Files () {
        if (DEBUG) Log.d(TAG, ">>>> start scanning for mp3 files...");
        String rootDir = "/sdcard" ;
        traverseScanMP3Files(new File(rootDir));
        if (DEBUG) Log.d(TAG, ">>>> done scanning for mp3 files...");
    }

    private void traverseScanMP3Files (File dir) {
        if (dir.exists()) {
//            if (DEBUG) Log.d(TAG, ">>>> scanning dir: " + dir.getAbsoluteFile());
            if (dir.getName().startsWith(".") || dir.getAbsolutePath().startsWith("/sdcard/Android/"))
                return;

            File[] files = dir.listFiles();
            if (files == null)
                return;

            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                if (file.isDirectory()) {
                    traverseScanMP3Files(file);
                } else {
                    if (file.getName().toLowerCase().endsWith("mp3")) {
                        String filePath = file.getAbsolutePath();

                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(filePath);

                        String duration = Util.ensureNotNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), "");

                        int intDuration = 0;
                        if (duration != null && duration.length() > 0)
                            intDuration = Integer.parseInt(duration);

                        if (intDuration == 0)
                            continue;

                        String title = Util.ensureNotNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), file.getName());
                        String artist = Util.ensureNotNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), "");
                        String album = Util.ensureNotNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), "");

                        int albumId = 0;
                        int artistId = 0;
                        if (!album.equals(""))
                            albumId = mMusicPlayerDAO.addAlbum(album);
                        if (!artist.equals(""))
                            artistId = mMusicPlayerDAO.addArtist(artist);

                        mMusicPlayerDAO.addSong(title, artistId, artist, albumId, album, intDuration, filePath);

                        if (DEBUG)
                            Log.d(TAG, ">>>> song info: " + artist + ", " + title + ", " + album + ", " + duration + ", " + artistId + ", " + albumId);

                    }
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play_prev_song: {
                if (mMusicPlayerService.getCurrentSong() != null)
                    mMusicPlayerService.playPrevSong();
                else
                    tryPlayingLastSong();
                break;
            }
            case R.id.btn_play_and_pause:
                if (mMusicPlayerService.isPlayingSong()) {
                    mMusicPlayerService.pausePlayback();
                    mPrefs.edit()
                            .putInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, mMusicPlayerService.getCurrentSong().id)
                            .putInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_PROGRESS, mMusicPlayerService.getPlayingProgress())
                            .commit();
                } else {
                    if (mMusicPlayerService.getCurrentSong() == null) {
                        tryPlayingLastSong();
                    } else {
                        mMusicPlayerService.resumePlayback();
                    }
                }
                break;
            case R.id.btn_play_next_song: {
                if (mMusicPlayerService.getCurrentSong() != null)
                    mMusicPlayerService.playNextSong();
                else
                    tryPlayingLastSong();
                break;
            }
        }
    }

    private void tryPlayingLastSong () {
        final int lastPlayedSongId = mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, 0);
        if (lastPlayedSongId > 0) {
            TaskExecutor.executeTask(new Runnable() {
                @Override
                public void run() {
                    // initialize the play list
                    mApp.setCurrentPlayList(mApp.getCachedAllMusicSongList(true));
                }
            });

            mApp.startPlayingSong(lastPlayedSongId, mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_PROGRESS, 0));
        }
    }

    private void playPrevSong(Song currentSong, List<Song> songList) {
        if (songList.size() > 0) {
            if (currentSong == null) {
                int lastPlayedSongId = mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, 0);
                if (lastPlayedSongId > 0)
                    currentSong = mMusicPlayerDAO.getSongById(lastPlayedSongId);
            }

            int prevSongIndex = -1;
            if (currentSong != null) {
                prevSongIndex = songList.indexOf(currentSong);
                if (prevSongIndex != -1) {
                    if (prevSongIndex > 0)
                        --prevSongIndex;
                    else
                        prevSongIndex = songList.size() - 1;
                }
            }
            if (prevSongIndex == -1)
                prevSongIndex = 0;

            mApp.startPlayingSong(songList.get(prevSongIndex).id, 0);
        }
    }

    private void playNextSong(Song currentSong, List<Song> songList) {
        if (songList.size() > 0) {
            if (currentSong == null) {
                int lastPlayedSongId = mPrefs.getInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, 0);
                if (lastPlayedSongId > 0)
                    currentSong = mMusicPlayerDAO.getSongById(lastPlayedSongId);
            }

            int nextSongIndex = -1;
            if (currentSong != null) {
                nextSongIndex = songList.indexOf(currentSong);
                if (nextSongIndex != -1) {
                    if (nextSongIndex < songList.size() - 1)
                        ++nextSongIndex;
                    else
                        nextSongIndex = 0;
                }
            }
            if (nextSongIndex == -1)
                nextSongIndex = 0;

            mApp.startPlayingSong(songList.get(nextSongIndex).id, 0);
        }
    }

    @Override
    public void onReceiveMessage(Message message) {
        switch (message.type) {
            case ON_START_PLAYBACK:
                onStartPlayback((Song) message.data);
                break;
            case ON_RESUME_PLAYBACK:
                onResumePlayback((Song) message.data);
                break;
            case ON_PAUSE_PLAYBACK:
                onPausePlayback((Song) message.data);
                break;
            case ON_UPDATE_PLAYING_PROGRESS:
                onUpdatePlayingProgress((Integer) message.data);
                break;
            case SHOW_FRAGMENT_MUSIC_LIST:
                MessageData2<Integer, SongGroup> messageData2 = (MessageData2<Integer, SongGroup>) message.data;
                mMusicListFragment.getArguments().putInt(LIST_TYPE, messageData2.o1);
                if (messageData2.o2 != null) {
                    mMusicListFragment.getArguments().putInt(EXTRA_ID, messageData2.o2.id);
                    mMusicListFragment.getArguments().putString(EXTRA_TITLE, messageData2.o2.name);
                } else {
                    mMusicListFragment.getArguments().putString(EXTRA_TITLE, getResources().getString(R.string.title_all_music));
                }
                showFragment(mMusicListFragment);
                break;
            case SHOW_FRAGMENT_ARTIST_LIST:
                showFragment(mArtistListFragment);
                break;
            case SHOW_FRAGMENT_ALBUM_LIST:
                showFragment(mAlbumListFragment);
                break;
        }
    }

    private void onStartPlayback (Song song) {
        mBtnPlayAndPause.setImageResource(R.drawable.icon_pause_selector);

        setInfoForCurSong(song, mMusicPlayerService.getPlayingProgress());
    }

    private void setInfoForCurSong(Song song, int progress) {
        mCurSongTitle.setText(song.title);
        mCurArtist.setText(song.artist);
        mCurSongProgress.setText(Util.formatMilliseconds(progress, null));
        mCurSongDuration.setText("/" + Util.formatMilliseconds(song.duration, null));
    }

    private void onResumePlayback (Song song) {
        mBtnPlayAndPause.setImageResource(R.drawable.icon_pause_selector);
    }

    private void onPausePlayback (Song song) {
        mBtnPlayAndPause.setImageResource(R.drawable.icon_play_selector);

        if (DEBUG) Log.d(TAG, ">>>> paused, current progress: " + mMusicPlayerService.getPlayingProgress());

        mPrefs.edit()
                .putInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_ID, mMusicPlayerService.getCurrentSong().id)
                .putInt(MusicPlayerApplication.PREF_KEY_LAST_PLAYED_SONG_PROGRESS, mMusicPlayerService.getPlayingProgress())
                .commit();
    }

    private StringBuilder mProgressBuffer = new StringBuilder();
    private void onUpdatePlayingProgress (int milliseconds) {
        mCurSongProgress.setText(Util.formatMilliseconds(milliseconds, mProgressBuffer));
        mProgressBuffer.delete(0, mProgressBuffer.length());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            mDestroyed = true;

            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
        if (!mMusicPlayerService.isPlayingSong())
            stopService(new Intent(this, MusicPlayerService.class));
    }
}
