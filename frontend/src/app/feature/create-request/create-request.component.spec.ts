import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CreateRequestComponent } from './create-request.component';
import { ServiceProviderService } from '../../api/services/service-provider.service';

describe('CreateRequestComponent', () => {
  let fixture: ComponentFixture<CreateRequestComponent>;
  let component: CreateRequestComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, CreateRequestComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    spyOn(ServiceProviderService.prototype, 'listActive').and.returnValue(of([]));

    fixture = TestBed.createComponent(CreateRequestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates successfully', () => {
    expect(component).toBeTruthy();
  });

  it('form is invalid when empty', () => {
    expect(component.form.invalid).toBeTrue();
  });

  it('form is valid with all required fields', () => {
    component.form.setValue({
      vehicleId: 'VH-001',
      description: 'Oil change needed',
      priority: 'MEDIUM',
    });
    expect(component.form.valid).toBeTrue();
  });

  it('vehicleId control is required', () => {
    const ctrl = component.form.controls.vehicleId;
    ctrl.setValue('');
    expect(ctrl.hasError('required')).toBeTrue();
  });

  it('description control has maxLength 2000 validator', () => {
    const ctrl = component.form.controls.description;
    ctrl.setValue('a'.repeat(2001));
    expect(ctrl.hasError('maxlength')).toBeTrue();
  });

  it('description control accepts value within maxLength', () => {
    const ctrl = component.form.controls.description;
    ctrl.setValue('a'.repeat(2000));
    expect(ctrl.hasError('maxlength')).toBeFalse();
  });

  it('priority control is required', () => {
    const ctrl = component.form.controls.priority;
    ctrl.setValue('');
    expect(ctrl.hasError('required')).toBeTrue();
  });
});
