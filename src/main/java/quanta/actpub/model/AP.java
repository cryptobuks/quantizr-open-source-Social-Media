package quanta.actpub.model;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Because the ActivityPup spec has lots of places where the object types are completely variable,
 * there's no clean way to use perfect type safety on all objects (hard coded properties), so
 * instead of having a POJO for the the various types of objects we use the accessor methods and
 * properties in this object.
 */
@Slf4j 
public class AP {
    public static boolean apHasProps(Object obj) {
        return obj instanceof Map<?, ?> || obj instanceof LinkedHashMap<?, ?>;
    }

    /**
     * Looks in all elements of list, to find all elements that are Objects, and returns the value of
     * the first one containing the prop val as a property of it
     */
    public static Object apParseList(List list, String prop) {
        if (list == null)
            return null;
        for (Object element : list) {
            // see if we can get it, no matter what type element is
            Val<Object> val = getFromMap(element, prop);
            if (val != null) {
                return val.getVal();
            }
        }
        return null;
    }

    public static String apStr(Object obj, String prop) {
        return apStr(obj, prop, true);
    }

    public static String apStr(Object obj, String prop, boolean warnIfMissing) {
        Val<Object> val = null;

        if ((val = getFromMap(obj, prop)) != null) {
            if (val.getVal() == null) {
                return null;
            } else if (val.getVal() instanceof String) {
                return (String) val.getVal();
            } else if (val.getVal() instanceof List) {
                // this can happen in normal flow now so I need a 'silent' argument to hide this when we need to.
                // ExUtil.error("Attempted to read prop " + prop + " from the following object as a string but it was an array: "
                //         + XString.prettyPrint(obj));
                return null;
            } else {
                if (warnIfMissing) {
                    ExUtil.warn("unhandled type on apStr() return val: "
                            + (val.getVal() != null ? val.getVal().getClass().getName() : "null on object")
                            + "\nUnable to get property " + prop + " from obj " + XString.prettyPrint(obj));
                }
                return null;
            }
        }

        if (warnIfMissing) {
            ExUtil.warn("unhandled type on apStr(): " + (obj != null ? obj.getClass().getName() : "null")
                    + "\nUnable to get property " + prop + " from obj " + XString.prettyPrint(obj));
        }
        return null;
    }

    public static Boolean apBool(Object obj, String prop) {
        Val<Object> val = null;

        if ((val = getFromMap(obj, prop)) != null) {
            if (val.getVal() == null) {
                return false;
            } else if (val.getVal() instanceof String) {
                return ((String) val.getVal()).equalsIgnoreCase(APConst.TRUE);
            } else if (val.getVal() instanceof Boolean) {
                return ((Boolean) val.getVal()).booleanValue();
            }
        }

        ExUtil.warn("unhandled type on apBool(): " + (obj != null ? obj.getClass().getName() : "null") + "Unable to get property "
                + prop + " from obj " + XString.prettyPrint(obj));
        return false;
    }

    public static Integer apInt(Object obj, String prop) {
        Val<Object> val = null;

        if ((val = getFromMap(obj, prop)) != null) {
            if (val.getVal() == null) {
                return 0;
            } else if (val.getVal() instanceof Integer) {
                return ((Integer) val.getVal()).intValue();
            } else if (val.getVal() instanceof Long) {
                return ((Long) val.getVal()).intValue();
            } else if (val.getVal() instanceof String) {
                return Integer.valueOf((String) val.getVal());
            }
        }

        ExUtil.warn("unhandled type on apInt(): " + (obj != null ? obj.getClass().getName() : "null") + "Unable to get property "
                + prop + " from obj " + XString.prettyPrint(obj));
        return 0;
    }

    public static Date apDate(Object obj, String prop) {
        Val<Object> val = null;

        if ((val = getFromMap(obj, prop)) != null) {
            if (val.getVal() == null) {
                return null;
            } else if (val.getVal() instanceof String) {
                return DateUtil.parseISOTime((String) val.getVal());
            }
        }

        ExUtil.warn("unhandled type on apDate(): " + (obj != null ? obj.getClass().getName() : "null") + "Unable to get property "
                + prop + " from obj " + XString.prettyPrint(obj));
        return null;
    }

    public static List<?> apList(Object obj, String prop, boolean allowConvertString) {
        Val<Object> val = null;

        if ((val = getFromMap(obj, prop)) != null) {
            if (val.getVal() == null) {
                return null;
            }
            // if we got an instance of a list return it
            else if (val.getVal() instanceof List<?>) {
                return (List<?>) val.getVal();
            }
            // if we expected a list and found a String, that's ok, return a list with one entry
            // the address 'to' and 'cc' properties can have this happen often.
            else if (allowConvertString && val.getVal() instanceof String) {
                return Arrays.asList(val.getVal());
            }
        }

        // todo-1: make this warning be triggered only by flag param 
        // ExUtil.warn("unhandled type on apList(): " + (ok(obj) ? obj.getClass().getName() : "null") + "Unable to get property "
        //         + prop + " from obj " + XString.prettyPrint(obj));
        return null;
    }

    /**
     * Gets an Object from 'prop' entry of map 'obj'
     */
    public static Object apObj(Object obj, String prop) {
        if (obj instanceof Map<?, ?>) {
            return ((Map<?, ?>) obj).get(prop);
        } else {
            ExUtil.warn("[1]getting prop " + prop + " from unsupported container type: "
                    + (obj != null ? obj.getClass().getName() : "null") + "Unable to get property " + prop + " from obj "
                    + XString.prettyPrint(obj));
        }
        return null;
    }

    /**
     * Makes an APObj from 'prop' entry of map 'obj'
     */
    public static APObj apAPObj(Object obj, String prop) {
        Object o = apObj(obj, prop);
        if (o instanceof Map<?, ?>) {
            return typeFromFactory(new APObj((Map<?, ?>) o));
        } else {
            // this is not an indication of a problem when we check for a property and don't find it.
            // ExUtil.warn("[2]getting prop " + prop + " from unsupported container type: "
            //         + (ok(obj) ? obj.getClass().getName() : "null") + "Unable to get property " + prop + " from obj "
            //         + XString.prettyPrint(obj));
        }
        return null;
    }

    public static Val<Object> getFromMap(Object obj, String prop) {
        if (obj instanceof Map<?, ?>) {
            return new Val<Object>(((Map<?, ?>) obj).get(prop));
        }
        return null;
    }

    /**
     * Returns a specific APObj-derived concrete class if we can, or else returns the same APObj passed
     * in.
     */
    public static APObj typeFromFactory(Object obj) {
        if (obj == null)
            return null;

        APObj ret = null;
        if (obj instanceof APObj) {
            ret = (APObj) obj;
        } else if (obj instanceof Map<?, ?>) {
            ret = new APObj((Map<?, ?>) obj);
        } else {
            throw new RuntimeException("Unable to convert type: " + obj.getClass().getName() + " to an APObj");
        }

        /* Parse "Activity" Objects */
        switch (ret.getType()) {
            case APType.Create:
                return new APOCreate(ret);

            case APType.Update:
                return new APOUpdate(ret);

            case APType.Follow:
                return new APOFollow(ret);

            case APType.Undo:
                return new APOUndo(ret);

            case APType.Delete:
                return new APODelete(ret);

            case APType.Accept:
                return new APOAccept(ret);

            case APType.Like:
                return new APOLike(ret);

            case APType.Announce:
                return new APOAnnounce(ret);

            case APType.Person:
                return new APOPerson(ret);

            case APType.Note:
                return new APONote(ret);

            default:
                // log.debug("using APObj for type: " + obj.getType());
                break;
        }
        return ret;
    }
}
