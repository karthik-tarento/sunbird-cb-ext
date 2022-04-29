package org.sunbird.scheduler.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.sunbird.cassandra.utils.CassandraOperation;
import org.sunbird.common.helper.cassandra.ServiceFactory;
import org.sunbird.common.util.Constants;
import org.sunbird.common.util.NotificationUtil;
import org.sunbird.common.util.PropertiesCache;
import org.sunbird.core.logger.CbExtLogger;
import org.sunbird.scheduler.model.CourseDetails;
import org.sunbird.scheduler.model.IncompleteCourses;
import org.sunbird.scheduler.model.UserCourseProgressDetails;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static org.sunbird.common.util.Constants.*;

public class EmailNotificationService implements Runnable {
    private static final NotificationUtil notificationUtil = new NotificationUtil();
    private static final CbExtLogger logger = new CbExtLogger(SchedulerManager.class.getName());
    static PropertiesCache p = PropertiesCache.getInstance();
    static Boolean sendNotification = Boolean.parseBoolean(p.getProperty(SEND_NOTIFICATION_PROPERTIES));
    static String notificationUrl = p.getProperty(NOTIFICATION_HOST) + p.getProperty(NOTIFICATION_ENDPOINT);
    static String courseUrl = p.getProperty(COURSE_URL);
    static String overviewBatchId = p.getProperty(OVERVIEW_BATCH_ID);
    static String senderMail = p.getProperty(SENDER_MAIL);
    static int lastAccessTimeGap = Integer.valueOf(p.getProperty(LAST_ACCESS_TIME_GAP)).intValue();

    private final CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    Gson gson = new Gson();
    Map<String, UserCourseProgressDetails> userCourseMap = new HashMap<>();
    Map<String, CourseDetails> courseIdAndCourseNameMap = new HashMap<String, CourseDetails>();

    public static void sendIncompleteCourseEmail(Map.Entry<String, UserCourseProgressDetails> entry) {
        try {
            if (!StringUtils.isEmpty(entry.getValue().getEmail()) && entry.getValue().getIncompleteCourses().size() > 0) {
                Map<String, Object> params = new HashMap();
                for (int i = 0; i < entry.getValue().getIncompleteCourses().size(); i++) {
                    int j=i+1;
                    params.put(COURSE + j, true);
                    params.put(COURSE + j + _URL, entry.getValue().getIncompleteCourses().get(i).getCourseUrl());
                    params.put(COURSE + j + THUMBNAIL, entry.getValue().getIncompleteCourses().get(i).getThumbnail());
                    params.put(COURSE + j + _NAME, entry.getValue().getIncompleteCourses().get(i).getCourseName());
                    params.put(COURSE + j + _DURATION, String.valueOf(entry.getValue().getIncompleteCourses().get(i).getCompletionPercentage()));

                }
                logger.info(entry.getValue().getEmail());
                notificationUtil.sendNotification(Arrays.asList(entry.getValue().getEmail()), params, senderMail, sendNotification, notificationUrl);
            }
        } catch (Exception e) {
            logger.info(String.format("Error in the incomplete courses email module %s", e.getMessage()));
        }
    }

    @Override
    public void run() {
        incompleteCourses();
    }

    public Map<String, UserCourseProgressDetails> incompleteCourses() {
        try {
            List<String> fields = new ArrayList<>(Arrays.asList(RATINGS_USER_ID, BATCH_ID_, COURSE_ID_, COMPLETION_PERCENTAGE_, LAST_ACCESS_TIME));
            Date date = new Date(new Date().getTime() - lastAccessTimeGap);
            List<Map<String, Object>> userCoursesList = cassandraOperation.searchByWhereClause(Constants.SUNBIRD_COURSES_KEY_SPACE_NAME, Constants.USER_CONTENT_CONSUMPTION, fields, date);
            if (!CollectionUtils.isEmpty(userCoursesList)) {
                fetchCourseIdsAndSetCourseNameAndThumbnail(userCoursesList);
                setUserCourseMap(userCoursesList, userCourseMap);
                getAndSetUserEmail();
                Iterator<Map.Entry<String, UserCourseProgressDetails>> it = userCourseMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, UserCourseProgressDetails> set = it.next();
                    sendIncompleteCourseEmail(set);
                }
                return userCourseMap;
            }
        } catch (IOException e) {
            logger.info(String.format("Error in the scheduler to send User Progress emails %s", e.getMessage()));
        }
        return null;
    }

    private void fetchCourseIdsAndSetCourseNameAndThumbnail(List<Map<String, Object>> userCoursesList) throws IOException {
        List<String> courseIds = new ArrayList<>();
        for (Map<String, Object> next : userCoursesList) {
            if (next.containsKey(Constants.COURSE_ID)) {
                if (!courseIds.contains((String) next.get(Constants.COURSE_ID)))
                    courseIds.add((String) next.get(Constants.COURSE_ID));
            }
        }
        getAndSetCourseName(courseIds);
    }

    private void getAndSetCourseName(List<String> courseIds) throws IOException {
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.IDENTIFIER, courseIds);
        List<String> fields = new ArrayList<>(Arrays.asList(Constants.IDENTIFIER, Constants.HIERARCHY));
        List<Map<String, Object>> coursesData = cassandraOperation.getRecordsByProperties(Constants.DEV_HIERARCHY_STORE, Constants.CONTENT_HIERARCHY, propertyMap, fields);
        for (Map<String, Object> map : coursesData) {
            if (map.get(Constants.IDENTIFIER) != null && map.get(Constants.HIERARCHY) != null && courseIdAndCourseNameMap.get(map.get(Constants.IDENTIFIER)) == null) {
                if (map.get(Constants.IDENTIFIER) != null && map.get(Constants.HIERARCHY) != null && courseIdAndCourseNameMap.get(map.get(Constants.IDENTIFIER)) == null) {
                    String courseData = map.get(Constants.HIERARCHY).toString();
                    Map<String, Object> courseDataMap = gson.fromJson(courseData, Map.class);
                    CourseDetails cd = new CourseDetails();
                    if (courseDataMap.get(NAME) != null) {
                        cd.setCourseName((String) courseDataMap.get(NAME));
                    }
                    if (courseDataMap.get(POSTER_IMAGE) != null) {
                        cd.setThumbnail((String) courseDataMap.get(POSTER_IMAGE));
                    }
                    courseIdAndCourseNameMap.put((String) map.get(Constants.IDENTIFIER), cd);
                }
            }
        }
    }

    private void getAndSetUserEmail() {
        ArrayList<String> userIds = new ArrayList<>(userCourseMap.keySet());
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        List<String> fields1 = new ArrayList<>(Arrays.asList(Constants.ID, Constants.PROFILE_DETAILS_KEY));
        List<Map<String, Object>> userEmail = cassandraOperation.getRecordsByProperties(Constants.SUNBIRD_KEY_SPACE_NAME, Constants.TABLE_USER, propertyMap, fields1);
        for (Map<String, Object> map : userEmail) {
            String email = null;
            String profileDetails = "";
            try {
                if (map.get(PROFILE_DETAILS_KEY) != null && userCourseMap.get(map.get(Constants.ID)) != null) {
                    profileDetails = (String) map.get(PROFILE_DETAILS_KEY);
                    HashMap<String, Object> hashMap = new ObjectMapper().readValue(profileDetails, HashMap.class);
                    HashMap<String, Object> personalDetailsMap = (HashMap<String, Object>) hashMap.get(PERSONAL_DETAILS);
                    if (personalDetailsMap.get(PRIMARY_EMAIL) != null) {
                        logger.info((String) personalDetailsMap.get(PRIMARY_EMAIL));
                        userCourseMap.get(map.get(ID)).setEmail((String) personalDetailsMap.get(PRIMARY_EMAIL));
                    }
                }
            } catch (Exception e) {
                logger.info(String.format("Error in get and set user email %s", e.getMessage()));
            }
        }
    }

    private void setUserCourseMap(List<Map<String, Object>> userCoursesList, Map<String, UserCourseProgressDetails> userCourseMap) {
        for (Map<String, Object> u : userCoursesList) {
            try {
                String courseId = (String) u.get(Constants.COURSE_ID);
                String batchId = (String) u.get(Constants.BATCH_ID);
                String userid = (String) u.get(Constants.USER_ID);
                if (courseId != null && batchId != null && courseIdAndCourseNameMap.get(courseId) != null && courseIdAndCourseNameMap.get(courseId).getThumbnail() != null) {
                    IncompleteCourses i = new IncompleteCourses();
                    i.setCourseId(courseId);
                    i.setCourseName(courseIdAndCourseNameMap.get(courseId).getCourseName());
                    i.setCompletionPercentage((Float) u.get(Constants.COMPLETION_PERCENTAGE));
                    i.setLastAccessedDate((Date) u.get(Constants.LAST_ACCESS_TIME));
                    i.setBatchId(batchId);
                    i.setThumbnail(courseIdAndCourseNameMap.get(courseId).getThumbnail());
                    if (courseId != null && batchId != null)
                        i.setCourseUrl(courseUrl + courseId + overviewBatchId + batchId);
                    if (userCourseMap.get(userid) != null) {
                        if (userCourseMap.get(userid).getIncompleteCourses().size() < 3) {
                            userCourseMap.get(userid).getIncompleteCourses().add(i);
                            if (userCourseMap.get(userid).getIncompleteCourses().size() == 3) {
                                userCourseMap.get(userid).getIncompleteCourses().sort(Comparator.comparing(IncompleteCourses::getLastAccessedDate).reversed());
                            }
                        }
                    } else {
                        UserCourseProgressDetails user = new UserCourseProgressDetails();
                        List<IncompleteCourses> ic = new ArrayList<>();
                        ic.add(i);
                        user.setIncompleteCourses(ic);
                        userCourseMap.put(userid, user);
                    }
                }
            } catch (Exception e) {
                logger.info(String.format("Error in set User Course Map %s", e.getMessage()));
            }
        }
    }
}