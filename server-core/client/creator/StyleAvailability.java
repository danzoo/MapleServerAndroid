package client.creator;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.DataDirectoryEntry;
import provider.DataFileEntry;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hair styles and face shapes a character may be created with.
 *
 * <p>Two tables hold the ids, and they are the whole authority: the create request is checked
 * against them and the creation page only ever walks what they hand out. They are seeded once, from
 * the art this server ships, so narrowing what can be created is a matter of deleting rows - the
 * client picks that up on the next character list.
 *
 * <p>The split between the genders is the numbering the creation pages have always used: faces
 * below 21000 and hairs below 31000 belong to men, the thousand above each belong to women. An id
 * outside those bands (the 33xxx and 34xxx event hairs) is offered to both.
 *
 * <p>Hair carries its colour in its last digit, which is not part of the style and so not part of
 * these tables: a colour is allowed simply because the art for it exists.
 */
public final class StyleAvailability {
    public static final String FACE_TABLE = "available_face";
    public static final String HAIR_TABLE = "available_hair";

    private static final Logger log = LoggerFactory.getLogger(StyleAvailability.class);

    private static final int MALE = 0;
    private static final int FEMALE = 1;
    private static final int FEMALE_FACE_BAND = 21;
    private static final int FEMALE_HAIR_BAND = 31;
    private static final int MALE_FACE_BAND = 20;
    private static final int MALE_HAIR_BAND = 30;

    private static final Set<Integer> male_faces = new HashSet<>();
    private static final Set<Integer> female_faces = new HashSet<>();
    private static final Set<Integer> male_hairs = new HashSet<>();
    private static final Set<Integer> female_hairs = new HashSet<>();
    private static final Set<Integer> hair_art = new HashSet<>();

    private StyleAvailability() {
    }

    /**
     * Creates the tables if this database has never seen them, fills them from the art if they are
     * still empty, and reads them back into memory. Call once while the server comes up.
     */
    public static void load(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + FACE_TABLE + " (id INTEGER NOT NULL, gender INTEGER NOT NULL, PRIMARY KEY (id, gender))");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + HAIR_TABLE + " (id INTEGER NOT NULL, gender INTEGER NOT NULL, PRIMARY KEY (id, gender))");

        List<Integer> face_ids = art_ids("Face");

        hair_art.clear();
        hair_art.addAll(art_ids("Hair"));

        Set<Integer> hair_styles = new HashSet<>();
        for (int hair : hair_art) {
            hair_styles.add(hair - (hair % 10));
        }

        seed(db, FACE_TABLE, rows(face_ids, MALE_FACE_BAND, FEMALE_FACE_BAND));
        seed(db, HAIR_TABLE, rows(new ArrayList<>(hair_styles), MALE_HAIR_BAND, FEMALE_HAIR_BAND));

        male_faces.clear();
        female_faces.clear();
        male_hairs.clear();
        female_hairs.clear();

        read(db, FACE_TABLE, male_faces, female_faces);
        read(db, HAIR_TABLE, male_hairs, female_hairs);

        log.info("Style availability for creation: faces " + male_faces.size() + " male / "
                + female_faces.size() + " female, hairs " + male_hairs.size() + " male / "
                + female_hairs.size() + " female");
    }

    public static boolean allows_face(int face, boolean male) {
        return (male ? male_faces : female_faces).contains(face);
    }

    /**
     * Takes the hair id as the client sends it, colour digit and all: the tables hold styles.
     */
    public static boolean allows_hair(int hair, boolean male) {
        int style = hair - (hair % 10);

        return (male ? male_hairs : female_hairs).contains(style);
    }

    public static boolean has_hair_art(int hair) {
        return hair_art.contains(hair);
    }

    public static List<Integer> faces(boolean male) {
        return sorted(male ? male_faces : female_faces);
    }

    public static List<Integer> hairs(boolean male) {
        return sorted(male ? male_hairs : female_hairs);
    }

    private static List<Integer> sorted(Set<Integer> ids) {
        List<Integer> out = new ArrayList<>(ids);
        Collections.sort(out);

        return out;
    }

    /**
     * The ids the server's own art carries, read off the directory of loose image files the client
     * would be sent. Each is named 000&lt;id&gt;.img.
     */
    private static List<Integer> art_ids(String directory) {
        List<Integer> ids = new ArrayList<>();
        DataProvider provider = DataProviderFactory.getDataProvider(WZFiles.CHARACTER);
        DataDirectoryEntry dir = find_directory(provider.getRoot(), directory);

        if (dir == null) {
            log.error("No Character/" + directory + " directory in the wz data");
            return ids;
        }

        for (DataFileEntry file : dir.getFiles()) {
            String name = file.getName();

            if (!name.endsWith(".img")) {
                continue;
            }

            try {
                ids.add(Integer.parseInt(name.substring(0, name.length() - 4)));
            } catch (NumberFormatException e) {
                log.warn("Unexpected image file name in Character/" + directory + ": " + name);
            }
        }

        return ids;
    }

    private static DataDirectoryEntry find_directory(DataDirectoryEntry dir, String name) {
        for (DataDirectoryEntry sub : dir.getSubdirectories()) {
            if (sub.getName().equals(name)) {
                return sub;
            }
        }

        return null;
    }

    /**
     * One row per gender each id belongs to; an id outside both bands gets a row for each.
     */
    private static List<int[]> rows(List<Integer> ids, int male_band, int female_band) {
        List<int[]> out = new ArrayList<>();

        for (int id : ids) {
            int band = id / 1000;

            if (band == male_band) {
                out.add(new int[]{id, MALE});
            } else if (band == female_band) {
                out.add(new int[]{id, FEMALE});
            } else {
                out.add(new int[]{id, MALE});
                out.add(new int[]{id, FEMALE});
            }
        }

        return out;
    }

    private static void seed(SQLiteDatabase db, String table, List<int[]> rows) {
        if (row_count(db, table) > 0) {
            return;
        }

        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();

            for (int[] row : rows) {
                values.clear();
                values.put("id", row[0]);
                values.put("gender", row[1]);
                db.insert(table, null, values);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        log.info("Seeded " + table + " with " + rows.size() + " rows");
    }

    private static int row_count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        return 0;
    }

    private static void read(SQLiteDatabase db, String table, Set<Integer> for_male, Set<Integer> for_female) {
        try (Cursor cursor = db.rawQuery("SELECT id, gender FROM " + table, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }

            int id_index = cursor.getColumnIndex("id");
            int gender_index = cursor.getColumnIndex("gender");

            if (id_index == -1 || gender_index == -1) {
                return;
            }

            do {
                if (cursor.getInt(gender_index) == MALE) {
                    for_male.add(cursor.getInt(id_index));
                } else {
                    for_female.add(cursor.getInt(id_index));
                }
            } while (cursor.moveToNext());
        }
    }
}
