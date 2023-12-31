package com.example.services;

import com.example.bean.SigninBean;
import com.example.bean.SignupBean;
import com.example.errorhandler.ErrorTypes;
import com.example.model.UsersModelImpl;
import com.example.utils.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class UsersServicesImpl implements IUsersServices {
    @Override
    public void SignupUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String password = req.getParameter("password");

        Pair<Boolean, String> nameValidation = Validator.ValidateName(name);
        Pair<Boolean, String> emailValidation = Validator.ValidateEmail(email);
        Pair<Boolean, String> passwordValidation = Validator.ValidatePassword(password);

        res.setContentType("application/json");
        res.setCharacterEncoding("utf-8");

        HashMap<String, String> data = new HashMap<>();

        if (!nameValidation.first) {
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, nameValidation.second, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        } else if (!emailValidation.first) {
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, emailValidation.second, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        } else if (!passwordValidation.first) {
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, passwordValidation.second, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        }

        UsersModelImpl usersModel = new UsersModelImpl();
        SigninBean signinBean = usersModel.findUserByEmail(email);

        if (signinBean != null) {
            LoggerUtil
                    .getInstance(UsersServicesImpl.class)
                    .info(email + ": user already exists");
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, ErrorTypes.USER_ALREADY_EXISTS, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        }

        SignupBean signupBean = new SignupBean(name, email, password);
        usersModel = new UsersModelImpl();
        String userId = usersModel.createUser(signupBean);

        data.put("email", email);
        data.put("_id", userId);
        LoggerUtil
                .getInstance(UsersServicesImpl.class)
                .info(email + ": new user created");
        new JsonResponse(HttpServletResponse.SC_OK, "signup success", data, true)
                .convertJson()
                .sendResponse(res);
    }

    @Override
    public void SigninUser(HttpServletRequest req, HttpServletResponse res) throws IOException, ParseException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");

        res.setContentType("application/json");
        res.setCharacterEncoding("utf-8");

        HashMap<String, String> data = new HashMap<>();
        UsersModelImpl usersModel = new UsersModelImpl();
        SigninBean signinBean = usersModel.findUserByEmail(email);

        if (signinBean == null) {
            LoggerUtil
                    .getInstance(UsersServicesImpl.class)
                    .info(email + ": not found inside the database");
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, ErrorTypes.USER_DOES_NOT_EXISTS, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        }

        long timeDiff = TimeUtils.TimeDiff(signinBean.LAST_ATTEMPT);
        String timeDiffString = TimeUtils.TimeDiffString(timeDiff);

        if (signinBean.ATTEMPT_COUNT >= 3 && timeDiff > 0) {
            LoggerUtil
                    .getInstance(UsersServicesImpl.class)
                    .warn(email + ": user is locked for " + timeDiffString);
            data.put("time", "account is locked for 24 hours will start in " + timeDiffString);
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, ErrorTypes.ACCOUNT_LOCKED_FOR_24_HRS, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        }

        if (!Objects.equals(signinBean.Password, password)) {
            Pair<Date, Integer> failedData = usersModel.findUserByEmailAndUpdateDate(email);
            LoggerUtil
                    .getInstance(UsersServicesImpl.class)
                    .warn(email + ": user gave wrong password for " + failedData.second + " attempt");
            int attemptsLeft = 3 - failedData.second;
            data.put("attempts", Integer.toString((attemptsLeft)));
            new JsonResponse(HttpServletResponse.SC_BAD_REQUEST, ErrorTypes.INVALID_CREDENTIALS, data, false)
                    .convertJson()
                    .sendResponse(res);
            return;
        }

        usersModel.findUserByEmailAndUpdateAttempts(email, 0);

        data.put("email", email);
        data.put("name", signinBean.Name);

        new JsonResponse(HttpServletResponse.SC_OK, "signin success", data, true)
                .convertJson()
                .sendResponse(res);
    }
}
