package {{ cookiecutter.org_name_2 }}.{{ cookiecutter.package_name }}

import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {

    override fun onDestroy() {
        super.onDestroy()
        // Flet can start its Python engine only once per process. If Android closes this
        // screen but keeps the process alive, the next launch shows a blank page until
        // Force stop. Ending the process here makes every reopen a clean start.
        // (Screen rotation is not affected: isChangingConfigurations is true then.)
        if (!isChangingConfigurations) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
