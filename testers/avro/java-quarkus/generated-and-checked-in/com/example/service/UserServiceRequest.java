package com.example.service;

/** Sealed request interface for UserService RPC */
public sealed interface UserServiceRequest
    permits GetUserRequest, CreateUserRequest, DeleteUserRequest, NotifyUserRequest {}
