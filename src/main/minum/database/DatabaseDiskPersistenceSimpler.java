package minum.database;

import minum.logging.ILogger;
import minum.utils.ActionQueue;
import minum.utils.FileUtils;
import minum.utils.StacktraceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static minum.utils.FileUtils.writeString;
import static minum.utils.Invariants.mustBeTrue;

/**
 * This allows us to run some disk-persistence operations more consistently
 * on any data that extends from {@link SimpleDataType}
 * @param <T> the type of data we'll be persisting
 */
public class DatabaseDiskPersistenceSimpler<T> {

    /**
     * The suffix we will apply to each database file
     */
    static final String databaseFileSuffix = ".ddps";

    private final Path dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;

    /**
     * Constructs a disk-persistence class well-suited for your data.
     * <p>
     * There is a bit of subtlety to its use, see documentation on the params.
     * @param dbDirectory the directory where we will store this data, relative to
     *                    the location from which we run the application.  Recommend that
     *                    you use some consistency, and consider hierarchy.  For example,
     *                    you might want to store all your data at db/foo - foo being the
     *                    name of the type of data we're storing, and db being a place to
     *                    collect together all your directories.
     *<p>
     *                    If you're consistent enough, you will have a top-level directory
     *                    like "db", with a bunch of sub-directories corresponding to the
     *                    types of data.  But really there is a lot of flexibility, you
     *                    can do it another way if you want.
     * @param executorService The executorService is our interface to the system which we
     *                        use for parallel processing.  We hand this sucker off to
     *                        an internal {@link ActionQueue} which handles all these
     *                        operations asynchronously, so that data is *eventually*
     *                        written to disk.
     */
    public DatabaseDiskPersistenceSimpler(Path dbDirectory, ExecutorService executorService, ILogger logger) {
        this.dbDirectory = dbDirectory;
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, executorService).initialize();
        this.logger = logger;

        actionQueue.enqueue("create directory" + this.dbDirectory, () -> {
            try {
                FileUtils.makeDirectory(logger, this.dbDirectory);
            } catch (IOException ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            }
            return null;
        });

    }

    /**
     * This function will stop the minum.database persistence cleanly.
     * <p>
     * In order to do this, we need to wait for our threads
     * to finish their work.  In particular, we
     * have offloaded our file writes to [actionQueue], which
     * has an internal thread for serializing all actions
     * on our minum.database
     */
    public void stop() {
        actionQueue.stop();
    }

    /**
     * takes any serializable data and writes it to disk
     *
     * @param data the data we are writing
     */
    public void persistToDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        actionQueue.enqueue("persist data to disk", () -> {
            writeString(fullPath, data.serialize());

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
    }

    /**
     * Deletes a piece of data from the disk
     *
     * @param data the data we are serializing and writing
     */
    public void deleteOnDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        actionQueue.enqueue("delete data from disk", () -> {
            try {
                Files.delete(Path.of(fullPath));
            } catch (Exception ex) {
                logger.logAsyncError(() -> "failed to delete file "+fullPath+" during deleteOnDisk");
            }

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
        }


    public void updateOnDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        final var file = new File(fullPath);

        actionQueue.enqueue("update data on disk", () -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, data.serialize());

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
    }

    private String makeFullPathFromData(SimpleDataType<T> data) {
        return dbDirectory + "/" + data.getIndex() + databaseFileSuffix;
    }

    /**
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    public List<T> readAndDeserialize(SimpleDataType<T> instance) {
        if (! Files.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, creating empty list of data");
            return new ArrayList<>();
        }

        final var data = new ArrayList<T>();

        try (final var pathStream = Files.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path -> Files.exists(path) && Files.isRegularFile(path)).toList();
            for (Path p : listOfFiles) {
                String fileContents;
                fileContents = Files.readString(p);
                if (fileContents.isBlank()) {
                    logger.logDebug( () -> p.getFileName() + " file exists but empty, skipping");
                } else {
                    try {
                        data.add(instance.deserialize(fileContents));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to deserialize "+p+" with data (\""+fileContents+"\")");
                    }
                }
            }
        } catch (IOException e) { // if we fail to walk() the dbDirectory.  I don't even know how to test this.
            throw new RuntimeException(e);
        }
        return data;
    }

}
