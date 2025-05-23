/************************************************************************************

Filename	:	VrInputRight.c 
Content		:	Handles common controller input functionality
Created		:	September 2019
Authors		:	Simon Brown

*************************************************************************************/


#include "VrInput.h"


ovrInputStateTrackedRemote leftTrackedRemoteState_old;
ovrInputStateTrackedRemote leftTrackedRemoteState_new;
ovrTrackedController leftRemoteTracking_new;

ovrInputStateTrackedRemote rightTrackedRemoteState_old;
ovrInputStateTrackedRemote rightTrackedRemoteState_new;
ovrTrackedController rightRemoteTracking_new;


float remote_movementSideways;
float remote_movementForward;
float remote_movementUp;
float positional_movementSideways;
float positional_movementForward;
float snapTurn;

void Joy_GenerateButtonEvents(int oldbuttons, int newbuttons, int numbuttons, int base);

void handleTrackedControllerButton(ovrInputStateTrackedRemote * trackedRemoteState, ovrInputStateTrackedRemote * prevTrackedRemoteState, uint32_t button, int key)
{
    Joy_GenerateButtonEvents(prevTrackedRemoteState->Buttons & button ? 1 : 0, trackedRemoteState->Buttons & button ? 1 : 0, 1, key);
}

static void Matrix4x4_Transform (const matrix4x4 *in, const float v[3], float out[3])
{
    out[0] = v[0] * (*in)[0][0] + v[1] * (*in)[0][1] + v[2] * (*in)[0][2] + (*in)[0][3];
    out[1] = v[0] * (*in)[1][0] + v[1] * (*in)[1][1] + v[2] * (*in)[1][2] + (*in)[1][3];
    out[2] = v[0] * (*in)[2][0] + v[1] * (*in)[2][1] + v[2] * (*in)[2][2] + (*in)[2][3];
}

void Matrix4x4_CreateFromEntity( matrix4x4 out, const vec3_t angles, const vec3_t origin, float scale );

void rotateAboutOrigin(float v1, float v2, float rotation, vec2_t out)
{
    vec3_t temp = {0.0f, 0.0f, 0.0f};
    temp[0] = v1;
    temp[1] = v2;

    vec3_t v = {0.0f, 0.0f, 0.0f};
    matrix4x4 matrix;
    vec3_t angles = {0.0f, rotation, 0.0f};
    vec3_t origin = {0.0f, 0.0f, 0.0f};
    Matrix4x4_CreateFromEntity(matrix, angles, origin, 1.0f);
    Matrix4x4_Transform(&matrix, temp, v);

    out[0] = v[0];
    out[1] = v[1];
}

float length(float x, float y)
{
    return sqrtf(powf(x, 2.0f) + powf(y, 2.0f));
}

#define NLF_DEADZONE 0.1
#define NLF_POWER 2.2

float nonLinearFilter(float in)
{
    float val = 0.0f;
    if (in > NLF_DEADZONE)
    {
        val = in > 1.0f ? 1.0f : in;
        val -= NLF_DEADZONE;
        val /= (1.0f - NLF_DEADZONE);
        val = powf(val, NLF_POWER);
    }
    else if (in < -NLF_DEADZONE)
    {
        val = in < -1.0f ? -1.0f : in;
        val += NLF_DEADZONE;
        val /= (1.0f - NLF_DEADZONE);
        val = -powf(fabsf(val), NLF_POWER);
    }

    return val;
}

bool between(float min, float val, float max)
{
    return (min < val) && (val < max);
}

