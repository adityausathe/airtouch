# AirTouch - gesture based UX

### :heavy_check_mark: Partially Working
### :warning: Historical Project
This app was developed as an undergraduate mini-project. Its source-code may not adhere to standard conventions/practices and needs further refactoring.

## Background
This idea of approaching mobile UX from a different perspective is not entirely novel, but for me the inspiration came from screen-reader apps which are designed for vision-impaired users. Screen-reader apps, although very extensible, takes relatively a lot more time to accept simple commands. This frustates the user since he/she needs to perform such simple tasks like making a call, opening camera, checking messages, etc frequently.
If we envision the hybrid approach of device-motion based gestures and a traditional screen-reader then the result could be miraculous. The former UX methods can be used to launch simple yet frequent tasks, whereas the extensibility of the screen-reader UX can be reserved to perform more convoluted tasks.

## Functionality
- User can move his/her mobile device in air to draw a simple pattern. The pattern will be recognized by the AirTouch-app, it will perform the task associated with that pattern.
- User needs to tell the AirTouch-app to start and stop recording the pattern, by using a preconfigured hardware-button(volume buttons) presses.
- A few of the basic patterns that can be drawn using gesture-based UX are as following: Horizontal-line(--), Vertical-line (|), RHS-inclined(/), LHS-inclined(\). Of course this list is not exhaustive and can be extended to include a lot more basic and combinational patterns.
- Various tasks(like opening camera, checking messages, etc) can be assigned to the patterns via the config-UI.

## Implementation 
- The AirTouch-app is using accelerometer sensor data to decode the pattern created by moving the device. It runs a background-service to collect the sensor-data.
- The app needs triggers for it to know when to start recording a gesture(device-motion) and when to stop. Currently, pressing any volume button twice will start the recording and pressing it again(twice) will stop the recording of the pattern and trigger pattern-interpretation.
- Decoding of the patterns from sensor data is done using a heuristic algorithm.

## Dependencies
- Android Platform, API= 23,24

