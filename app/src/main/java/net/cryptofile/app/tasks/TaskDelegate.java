package net.cryptofile.app.tasks;

import net.cryptofile.app.data.Result;

public interface TaskDelegate {
    public void taskCompletionResult(Result result);

    public void taskProgress(int progress);

    public void taskStage(String stage);
}
