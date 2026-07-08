package com.shark.lobby.config;

import com.shark.lobby.dto.ErrorMessage;
import com.shark.lobby.exception.NotHostException;
import com.shark.lobby.exception.RoomFullException;
import com.shark.lobby.exception.RoomNotFoundException;
import com.shark.lobby.exception.RoomNotJoinableException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler({
            RoomNotFoundException.class,
            RoomNotJoinableException.class,
            RoomFullException.class,
            NotHostException.class,
            IllegalArgumentException.class
    })
    @SendToUser("/queue/errors")
    public ErrorMessage handleCustomExceptions(RuntimeException ex) {
        return new ErrorMessage(ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleException(Exception ex) {
        return new ErrorMessage("Error interno en el servidor de lobby");
    }
}
