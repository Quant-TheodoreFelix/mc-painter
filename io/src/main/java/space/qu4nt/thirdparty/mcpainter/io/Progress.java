package space.qu4nt.thirdparty.mcpainter.io;

@FunctionalInterface
public interface Progress {

    Progress NONE = (done, total) -> true;

    boolean report(long done, long total);
}
