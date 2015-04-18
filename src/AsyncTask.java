/**
 * Async task
 * Used to create a thread with 3 states
 * onPreExecute: do before stuff you need done
 * doInBackground: do the stuff
 * onPostExecute: do after the stuff you need done
 */
public abstract class AsyncTask implements Runnable {
    @Override
    public void run() {
        onPreExecute();
        doInBackground();
        onPostExecute();
    }

    protected abstract void onPreExecute();
    protected abstract void doInBackground();
    protected abstract void onPostExecute();
}
