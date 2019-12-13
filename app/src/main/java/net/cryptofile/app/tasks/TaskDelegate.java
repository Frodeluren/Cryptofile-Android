package net.cryptofile.app.tasks;

import net.cryptofile.app.data.Result;

public interface TaskDelegate {
    void taskCompletionResult(Result result);

    void taskProgress(float progress);

    void taskStage(String stage);
}
