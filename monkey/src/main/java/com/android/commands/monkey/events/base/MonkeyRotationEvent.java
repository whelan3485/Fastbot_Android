package com.android.commands.monkey.events.base;

import android.app.IActivityManager;
// IWindowManager 不再需要直接作为参数，但为了兼容旧的 injectEvent 签名，可以保留
import android.view.IWindowManager;
import com.android.commands.monkey.events.MonkeyEvent;
import com.android.commands.monkey.utils.Logger;
import java.io.IOException;

public class MonkeyRotationEvent extends MonkeyEvent {
    public final int mRotationDegree;
    public final boolean mPersist;

    public MonkeyRotationEvent(int degree, boolean persist) {
        super(EVENT_TYPE_ROTATION);
        mRotationDegree = degree;
        mPersist = persist;
    }

    private int executeShellCommand(String command) {
        try {
            // 在 shell 环境中执行命令
            Process process = Runtime.getRuntime().exec(command);
            // 等待命令执行完成，检查是否有错误
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Logger.warningPrintln("Shell command failed with exit code " + exitCode + " for: " + command);
                return MonkeyEvent.INJECT_FAIL;
            }
            return MonkeyEvent.INJECT_SUCCESS;
        } catch (IOException | InterruptedException e) {
            Logger.errorPrintln("Failed to execute shell command: " + command);
            e.printStackTrace();
            return MonkeyEvent.INJECT_FAIL;
        }
    }

    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        if (verbose > 0) {
            Logger.println(":Sending rotation degree=" + mRotationDegree + ", persist=" + mPersist);
        }

        // 步骤 1: 关闭自动旋转以准备锁定
        if (executeShellCommand("settings put system accelerometer_rotation 0") != MonkeyEvent.INJECT_SUCCESS) {
            return MonkeyEvent.INJECT_FAIL;
        }

        // 步骤 2: 设置固定的旋转方向
        if (executeShellCommand("settings put system user_rotation " + mRotationDegree) != MonkeyEvent.INJECT_SUCCESS) {
            // 如果失败，最好尝试恢复自动旋转
            executeShellCommand("settings put system accelerometer_rotation 1");
            return MonkeyEvent.INJECT_FAIL;
        }

        // 步骤 3: 如果不是持久化锁定，则恢复自动旋转 (模拟 thaw)
        if (!mPersist) {
            if (verbose > 0) {
                Logger.println(":Thawing rotation lock.");
            }
            return executeShellCommand("settings put system accelerometer_rotation 1");
        }

        return MonkeyEvent.INJECT_SUCCESS;
    }
}