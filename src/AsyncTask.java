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
