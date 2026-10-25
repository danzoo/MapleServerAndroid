/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package provider;

import android.graphics.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.wz.DataType;

public class DataTool {
    public static String getString(Data data) {
        final Object value = data.getData();
        // wz stores an absent value as an empty/void property, while some newer
        // archives reuse the slot with another type; treat "not a string" as absent.
        return value instanceof String ? (String) value : null;
    }
    private static final Logger log = LoggerFactory.getLogger(DataTool.class);

    public static String getString(Data data, String def) {
        if (data == null) {
            return def;
        }
        final String value = getString(data);
        return value != null ? value : def;
    }

    public static String getString(String path, Data data) {
        return getString(data.getChildByPath(path));
    }

    public static String getString(String path, Data data, String def) {
        return getString(data.getChildByPath(path), def);
    }

    public static double getDouble(Data data) {
        return ((Number) data.getData()).doubleValue();
    }

    public static float getFloat(Data data) {
        return ((Number) data.getData()).floatValue();
    }

    public static int getInt(Data data) {
        if (data == null || data.getData() == null) {
            return 0;// DEF?
        }
        return ((Number) data.getData()).intValue();
    }

    public static int getInt(String path, Data data) {
        return getInt(data.getChildByPath(path));
    }

    public static int getIntConvert(Data data) {
        if (data.getType() == DataType.STRING) {
            return Integer.parseInt(getString(data));
        } else {
            return getInt(data);
        }
    }

    public static int getIntConvert(Data data, int def) {
        if (data == null) {
            return def;
        }
        if (data.getType() == DataType.STRING) {
            String dd = getString(data);
            if (dd.endsWith("%")) {
                dd = dd.substring(0, dd.length() - 1);
            }
            try {
                return Integer.parseInt(dd);
            } catch (NumberFormatException nfe) {
                return def;
            }
        } else {
            return getInt(data, def);
        }
    }

    public static int getIntConvert(String path, Data data) {
        Data d = data.getChildByPath(path);
        if (d.getType() == DataType.STRING) {
            return Integer.parseInt(getString(d));
        } else {
            return getInt(d);
        }
    }

    public static int getInt(Data data, int def) {
        if (data == null || data.getData() == null) {
            return def;
        } else if (data.getType() == DataType.STRING) {
            return Integer.parseInt(getString(data));
        } else {
            Object numData = data.getData();
            if (numData instanceof Number) {
                return ((Number) numData).intValue();
            } else {
                return def;
            }
        }
    }

    public static int getInt(String path, Data data, int def) {
        return getInt(data.getChildByPath(path), def);
    }

    public static int getIntConvert(String path, Data data, int def) {
        Data d = data.getChildByPath(path);
        if (d == null) {
            return def;
        }
        if (d.getType() == DataType.STRING) {
            try {
                return Integer.parseInt(getString(d));
            } catch (NumberFormatException nfe) {
                log.error("getIntConvert error", nfe);
                return def;
            }
        } else {
            return getInt(d, def);
        }
    }

    public static Point getPoint(Data data) {
        return ((Point) data.getData());
    }

    public static Point getPoint(String path, Data data) {
        return getPoint(data.getChildByPath(path));
    }

    public static Point getPoint(String path, Data data, Point def) {
        final Data pointData = data.getChildByPath(path);
        if (pointData == null) {
            return def;
        }
        return getPoint(pointData);
    }

    public static String getFullDataPath(Data data) {
        String path = "";
        DataEntity myData = data;
        while (myData != null) {
            path = myData.getName() + "/" + path;
            myData = myData.getParent();
        }
        return path.substring(0, path.length() - 1);
    }
}
