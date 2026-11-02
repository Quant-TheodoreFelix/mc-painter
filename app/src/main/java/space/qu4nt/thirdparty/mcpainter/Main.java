package space.qu4nt.thirdparty.mcpainter;

import java.nio.file.Path;
import space.qu4nt.thirdparty.mcpainter.io.SmokeTest;

public class Main {
    static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("smoke-test")) {
            for (String line : SmokeTest.run(Path.of(args[1]))) {
                IO.println(line);
            }
            return;
        }
        IO.println("usage: mc-painter smoke-test <world-directory>");
        System.exit(2);
    }
}
