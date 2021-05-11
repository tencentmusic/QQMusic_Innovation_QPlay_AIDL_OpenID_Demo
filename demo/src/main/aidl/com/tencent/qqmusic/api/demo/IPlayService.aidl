// IPlayService.aidl
package com.tencent.qqmusic.api.demo;

// Declare any non-default types here with import statements

import com.tencent.qqmusic.api.demo.IPrint;

interface IPlayService {
    void bindService();
    void startPcmMode();
    void stopPcmMode();
    void resumeOrPause();
    void playNext();
    void setPrintMessageCallback(IPrint callback);
}